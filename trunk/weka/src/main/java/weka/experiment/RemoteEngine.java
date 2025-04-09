/*
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/*
 *    RemoteEngine.java
 *    Copyright (C) 2000-2012 University of Waikato, Hamilton, New Zealand
 *
 */

package weka.experiment;

// --- NEW IMPORT ---
import java.io.ObjectInputFilter;
// --- END NEW IMPORT ---

import java.net.InetAddress;
import java.net.URL;
import java.net.URLClassLoader;
import java.rmi.Naming;
// import java.rmi.RMISecurityManager; // --- COMMENTED OUT / REMOVED ---
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Enumeration;
import java.util.Hashtable; // Consider ConcurrentHashMap for better concurrency if needed

import weka.core.Queue; // Consider java.util.concurrent.BlockingQueue for better concurrency
import weka.core.RevisionHandler;
import weka.core.RevisionUtils;
import weka.core.Utils;

/**
 * A general purpose server for executing Task objects sent via RMI.
 * <p>
 * !! SECURITY WARNING !! This version includes a basic serialization filter
 * as a minimal security improvement, but still lacks authentication and
 * transport encryption. Use with caution in untrusted networks. It is the
 * user's responsibility to test and adjust the serialization filter.
 *
 * @author Mark Hall (mhall@cs.waikato.ac.nz)
 * @version $Revision$
 */
public class RemoteEngine extends UnicastRemoteObject implements Compute,
  RevisionHandler {

  /** for serialization */
  private static final long serialVersionUID = -1021538162895448259L;

  /** The name of the host that this engine is started on */
  private String m_HostName = "local"; // Consider making final if set only in constructor

  /** A queue of waiting tasks - Consider replacing with ConcurrentLinkedQueue or LinkedBlockingQueue */
  private final Queue m_TaskQueue = new Queue();

  /** A queue of corresponding ID's for tasks - Needs to stay in sync with m_TaskQueue */
  private final Queue m_TaskIdQueue = new Queue();

  /** A hashtable of experiment status - Consider replacing with ConcurrentHashMap */
  private final Hashtable<String, TaskStatusInfo> m_TaskStatus = new Hashtable<String, TaskStatusInfo>();

  /** Is there a task running - needs careful synchronization or use AtomicBoolean */
  private volatile boolean m_TaskRunning = false; // volatile might help visibility but not atomicity

  /** Clean up interval (in ms) */
  protected static long CLEANUPTIMEOUT = 3600000; // 1 hour

  /**
   * Constructor
   *
   * @param hostName name of the host
   * @exception RemoteException if something goes wrong during export
   */
  public RemoteEngine(String hostName) throws RemoteException {
    // super() implicitly called, which exports the object.
    // If using custom socket factories (for SSL), export would be done explicitly later.
    m_HostName = hostName;

    // Launch the clean-up thread
    startCleanUpThread();
  }

  /** Starts the background thread for purging stale tasks. */
  private void startCleanUpThread() {
    Thread cleanUpThread;
      cleanUpThread = new Thread(() -> { // Use Lambda
        while (true) {
          try {
            Thread.sleep(CLEANUPTIMEOUT);
          } catch (InterruptedException ie) {
             System.err.println("Cleanup thread interrupted, exiting.");
             Thread.currentThread().interrupt(); // Re-interrupt thread status
             return; // Exit the loop if interrupted
          }

          // Check size before potentially synchronizing
          if (!m_TaskStatus.isEmpty()) {
             try {
                purge(); // purge() method handles its own synchronization
             } catch (Exception e) {
                 System.err.println("Error during periodic task purge: " + e.getMessage());
                 e.printStackTrace(System.err); // Log unexpected errors during purge
             }
          } else {
            // Optional: Reduce logging verbosity if desired
            // System.err.println("RemoteEngine : purge - no tasks to check.");
          }
        }
      });
      cleanUpThread.setName("RemoteEngine-CleanupThread");
    cleanUpThread.setPriority(Thread.MIN_PRIORITY);
      cleanUpThread.setDaemon(true); // Allow JVM to exit even if this thread runs
    cleanUpThread.start();
  }


  /**
   * Takes a task object and queues it for execution.
   * This method is synchronized to protect queue and status updates.
   *
   * @param t the Task object to execute
   * @return an identifier for the Task that can be used when querying Task status
   * @throws RemoteException if the task object is null or queuing fails unexpectedly.
   */
  @Override
  public synchronized Object executeTask(Task t) throws RemoteException {
    if (t == null) {
        // Log the attempt to execute a null task
        System.err.println("Warning: Received null Task object from client.");
        throw new RemoteException("Received null Task object.");
    }

    // Simple ID generation. Consider UUID for more uniqueness.
    String taskId = System.currentTimeMillis() + ":" + t.hashCode();

    addTaskToQueue(t, taskId); // This is called from a synchronized method

    return taskId;
  }

  /**
   * Returns status information on a particular task.
   * This method is synchronized to protect access to m_TaskStatus.
   *
   * @param taskId the ID of the task to check (must be a String)
   * @return a <code>TaskStatusInfo</code> encapsulating task status info
   * @throws Exception if the taskId is invalid or the task is not found.
   */
  @Override
  public synchronized Object checkStatus(Object taskId) throws Exception {
    if (taskId == null || !(taskId instanceof String)) {
        throw new IllegalArgumentException("Task ID must be a non-null String.");
    }
    String taskIdStr = (String) taskId;

    TaskStatusInfo inf = m_TaskStatus.get(taskIdStr);

    if (inf == null) {
      // Make error message clearer
      throw new Exception("RemoteEngine (" + m_HostName + ") : Task with ID [" + taskIdStr + "] not found.");
    }

    // Create a *copy* of the status to return to the client.
    // Assumes TaskStatusInfo is reasonably cloneable or has a copy constructor.
    // If not, create a new one and copy fields manually.
    TaskStatusInfo result = new TaskStatusInfo(); // Simplest approach if no copy mechanism
    result.setExecutionStatus(inf.getExecutionStatus());
    result.setStatusMessage(inf.getStatusMessage());
    // Decide carefully if the result object should be included.
    // Cloning results can be complex/expensive. Returning null might be safer
    // if the client is expected to fetch results separately or if results are large.
    // For simplicity here, we copy the reference, assuming TaskResult is immutable or handled safely.
    result.setTaskResult(inf.getTaskResult());


    // Remove finished/failed tasks *after* status is checked and result copied.
    if (result.getExecutionStatus() == TaskStatusInfo.FINISHED
      || result.getExecutionStatus() == TaskStatusInfo.FAILED) {
      System.err.println("Status checked for finished/failed Task ID: " + taskIdStr + ". Removing from status map.");
      m_TaskStatus.remove(taskIdStr);
      // Optional: Help GC by nulling references within the removed object
      // inf.setTaskResult(null);
    }

    return result;
  }

  /**
   * Adds a new task to the queue and updates status map.
   * Assumes it's called from a synchronized context (like executeTask).
   *
   * @param t a <code>Task</code> value to be added
   * @param taskId the id of the task to be added
   */
  private void addTaskToQueue(Task t, String taskId) { // No need for separate sync if called by sync method
    TaskStatusInfo newTaskStatus = t.getTaskStatus(); // Get initial status from task if provided
    if (newTaskStatus == null) {
      newTaskStatus = new TaskStatusInfo(); // Create new status if task doesn't provide one
    } else {
      // Consider cloning the status object from the task to avoid shared mutable state
      // newTaskStatus = cloneStatus(newTaskStatus); // If a clone method exists
    }

    // Set initial status before putting in map
    newTaskStatus.setExecutionStatus(TaskStatusInfo.TO_BE_RUN); // Assuming QUEUED constant exists
    newTaskStatus.setStatusMessage("RemoteEngine (" + m_HostName + ") : task "
      + taskId + " queued.");

    // Add task and ID to their respective queues
    m_TaskQueue.push(t);
    m_TaskIdQueue.push(taskId);

    // Add status to map
    m_TaskStatus.put(taskId, newTaskStatus);

    System.err.println("Task ID: " + taskId + " Queued. Queue size: " + m_TaskQueue.size());

    // Attempt to start a task if none is running
    // This check and call should ideally be atomic with the queue push,
    // ensured by the calling method being synchronized.
    if (!m_TaskRunning) {
      startTask(); // startTask handles its own synchronization
    }
  }

  /**
   * Checks for waiting tasks and starts one if none is running.
   * This method is synchronized to ensure only one task starts at a time
   * and to safely access shared state (m_TaskRunning, queues).
   */
  private synchronized void startTask() {

    if (m_TaskRunning) {
        return; // Another task is already running or being started
    }

    if (!m_TaskQueue.isEmpty()) { // Safely check queue size
      m_TaskRunning = true; // Mark as running *before* starting thread

      // Retrieve task details within this synchronized block
      final Task currentTask = (Task) m_TaskQueue.pop();
      final String taskId = (String) m_TaskIdQueue.pop();
      final TaskStatusInfo tsi = m_TaskStatus.get(taskId);

      if (currentTask == null || taskId == null || tsi == null) {
           System.err.println("CRITICAL ERROR starting task: Failed to retrieve consistent task details for TaskID: " + taskId);
           m_TaskRunning = false; // Reset running state
           // Consider attempting to start the *next* task if queue isn't empty?
           // if (!m_TaskQueue.isEmpty()) { startTask(); }
           return;
      }

      System.err.println("Preparing to launch task ID: " + taskId + "...");

      // Create and start the worker thread
      Thread activeTaskThread = new Thread(() -> { // Use Lambda
        try {
            // Update status to PROCESSING
          tsi.setExecutionStatus(TaskStatusInfo.PROCESSING);
            tsi.setStatusMessage("RemoteEngine (" + m_HostName + ") : task " + taskId + " running...");
            System.err.println("Task ID: " + taskId + " started execution.");

            // *** Execute the task ***
            currentTask.execute();
            // *** Task execution finished ***

            System.err.println("Task ID: " + taskId + " execution completed.");

            // Update status based on task's final state
            TaskStatusInfo runStatus = currentTask.getTaskStatus();
            if (runStatus != null) {
            tsi.setExecutionStatus(runStatus.getExecutionStatus());
                tsi.setStatusMessage("RemoteEngine (" + m_HostName + ") " // Use consistent prefix
              + runStatus.getStatusMessage());
                tsi.setTaskResult(runStatus.getTaskResult()); // Store result reference
            } else {
                 // Task finished without error but didn't provide status update
                 tsi.setExecutionStatus(TaskStatusInfo.FINISHED);
                 tsi.setStatusMessage("RemoteEngine (" + m_HostName + ") Task " + taskId + " finished without specific status.");
            }

        } catch (Error err) { // Catch Errors (like OutOfMemoryError, StackOverflowError)
            System.err.println("Task ID: " + taskId + " failed with FATAL ERROR: " + err.toString());
            err.printStackTrace(System.err); // Log stack trace
            tsi.setExecutionStatus(TaskStatusInfo.FAILED);
            tsi.setStatusMessage("RemoteEngine (" + m_HostName + ") : Task " + taskId + " failed with Error: " + err.getMessage());
        } catch (Exception ex) { // Catch standard Exceptions
            System.err.println("Task ID: " + taskId + " failed with EXCEPTION: " + ex.toString());
            ex.printStackTrace(System.err); // Log stack trace
            tsi.setExecutionStatus(TaskStatusInfo.FAILED);
            tsi.setStatusMessage("RemoteEngine (" + m_HostName + ") : Task " + taskId + " failed with Exception: " + ex.getMessage());
           } finally {
            // --- Critical section: Reset state and check for next task ---
            synchronized(RemoteEngine.this) { // Ensure atomic update and check
                m_TaskRunning = false; // Mark task as no longer running
                System.err.println("Task ID: " + taskId + " processing finished. Checking for next task.");
                // Immediately check if another task can be started to avoid idle time
                 if (!m_TaskQueue.isEmpty()) {
                    startTask(); // Recursively call startTask to potentially start the next one
                } else {
                    System.err.println("Task queue is empty. Engine idle.");
                    // Optional: Consider class purging only when truly idle
                    // if (m_TaskStatus.isEmpty()) { purgeClasses(); }
            }
          }
            // --- End Critical section ---
        }
      }); // End Lambda for thread run()

      activeTaskThread.setName("RemoteEngine-Worker-" + taskId);
      activeTaskThread.setPriority(Thread.MIN_PRIORITY); // Keep low priority? Or configurable?
      activeTaskThread.setDaemon(false); // Worker threads should probably not be daemons
      activeTaskThread.start();

    } else {
        // This block should theoretically not be reached if called correctly,
        // but added for completeness.
        // System.err.println("startTask called but queue is empty.");
    }
  }


  /**
   * Attempts to suggest garbage collection.
   * !! Class unloading via ClassLoaders is generally unreliable here !!
   */
  private void purgeClasses() {
     System.err.println("Suggesting garbage collection (actual class unloading effectiveness varies)...");
     System.gc();
     // System.runFinalization(); // <-- REMOVED: Finalization is deprecated and unreliable.
  }

  /**
   * Periodically checks the status map for stale (old and finished/failed) tasks
   * and removes them to prevent memory leaks if clients abandon tasks.
   * This method is synchronized to safely iterate and modify the shared m_TaskStatus map.
   */
  private synchronized void purge() {
    long currentTime = System.currentTimeMillis();
    System.err.println("RemoteEngine: Running periodic task purge check. Current time: " + currentTime);
    int initialSize = m_TaskStatus.size();
    int removedCount = 0;

    // Use Hashtable's keys enumeration and check map within loop for safety,
    // although synchronizing the whole method makes direct iteration safer.
    Enumeration<String> keys = m_TaskStatus.keys();
    while (keys.hasMoreElements()) {
      String taskId = keys.nextElement();
       TaskStatusInfo tsi = m_TaskStatus.get(taskId); // Get within synchronized block

       if (tsi == null) continue; // Task was removed concurrently? Should not happen here.

       boolean stale = false;
       try {
            // Extract timestamp assuming format "timestamp:hashcode"
      String timeString = taskId.substring(0, taskId.indexOf(':'));
            long taskTimestamp = Long.parseLong(timeString);
            if (currentTime - taskTimestamp > CLEANUPTIMEOUT) {
                stale = true;
            }
       } catch (Exception e) {
            // Log issues parsing the ID format, might indicate a problem
            System.err.println("Warning: Could not parse timestamp from task ID format: '" + taskId + "' - " + e.getMessage());
            // Decide policy: remove malformed/old IDs? For now, skip them.
            continue;
        }

       // Check if the task is both stale AND in a finished/failed state
       if (stale && (tsi.getExecutionStatus() == TaskStatusInfo.FINISHED || tsi.getExecutionStatus() == TaskStatusInfo.FAILED)) {
           System.err.println("Task ID: " + taskId + " (Status: " + tsi.getExecutionStatus() + ") is stale. Removing.");
           m_TaskStatus.remove(taskId); // Safe removal within synchronized block
           removedCount++;
           // Optional: Help GC
           // tsi.setTaskResult(null);
       }
    } // End while loop

    if (removedCount > 0) {
        System.err.println("Purge check complete. Removed " + removedCount + " stale tasks. Size before: " + initialSize + ", Size after: " + m_TaskStatus.size());
        // Optional: Suggest GC after removing items
        // System.gc();
      } else {
        // Optional: Reduce verbosity if no tasks were removed
        // System.err.println("Purge check complete. No stale tasks found to remove.");
    }
  }

  /**
   * Returns the revision string.
   *
   * @return the revision
   */
  @Override
  public String getRevision() {
    return RevisionUtils.extract("$Revision$");
  }

  // --- Centralized place for the Serialization Filter ---
  /**
   * Creates a serialization filter to allow only known safe classes.
   * !! IMPORTANT !! This filter is a BEST GUESS and likely needs adjustment
   * for your specific Weka tasks. Test thoroughly! Denying essential classes
   * will break functionality. Allowing too much reduces security.
   *
   * @return An ObjectInputFilter instance.
   */
  private static ObjectInputFilter createRmiFilter() {
      // Define allowed patterns using semicolon separators
      // Start with basic Java types, collections, and essential Weka classes.
      String allowedPatterns = String.join(";",
          // Primitives and common wrappers
          "boolean", "byte", "char", "short", "int", "long", "float", "double",
          "java.lang.Boolean", "java.lang.Byte", "java.lang.Character",
          "java.lang.Short", "java.lang.Integer", "java.lang.Long",
          "java.lang.Float", "java.lang.Double",
          "java.lang.String", "java.lang.Number",
          "java.math.BigDecimal", "java.math.BigInteger",
          // Common collections (allow common interfaces and implementations)
          "java.util.List", "java.util.ArrayList", "java.util.LinkedList",
          "java.util.Map", "java.util.HashMap", "java.util.Hashtable", "java.util.TreeMap",
          "java.util.Set", "java.util.HashSet", "java.util.TreeSet",
          "java.util.Vector", // Used by older Weka code sometimes?
          "java.util.Properties",
          // Weka core classes often needed for tasks/results
          "weka.core.Attribute",
          "weka.core.Instance", // Base interface/class
          "weka.core.BinarySparseInstance", "weka.core.DenseInstance", "weka.core.SparseInstance", // Common implementations
          "weka.core.Instances",
          "weka.core.Range", "weka.core.SelectedTag",
          "weka.core.Tag",
          "weka.core.Capabilities", // Often part of classifiers/filters
          "weka.core.RevisionHandler", // Interface
          "weka.core.Option", // For options handling
          // Weka experiment classes
          "weka.experiment.Task", // Base interface/class for tasks
          "weka.experiment.TaskStatusInfo", // For status checking
          "weka.experiment.Compute", // RMI interface
          // --> !! IMPORTANT: Add specific Task implementations used by YOUR clients !! <--
          // --> !! e.g., "weka.experiment.CrossValidationResultProducer"           !! <--
          // --> !! e.g., "weka.experiment.LearningRateResultProducer"              !! <--
          // --> !! e.g., "weka.experiment.RandomSplitResultProducer"               !! <--

          // Allow specific Classifiers/Filters IF they are sent as part of the Task object
          // This requires careful consideration. Allowing broad packages is risky.
          // Be specific if possible, e.g.:
          // "weka.classifiers.trees.J48",
          // "weka.classifiers.functions.LinearRegression",
          // "weka.filters.unsupervised.attribute.Remove",
          // Or allow base classes if necessary:
          // "weka.classifiers.Classifier", // Base class
          // "weka.filters.Filter",       // Base class

          // Allow arrays of allowed types (primitive and object)
          "*\\[\\]", // Basic array syntax
          // Basic necessities for serialization mechanism
          "java.lang.Object",
          "java.io.Serializable" // Marker interface
          // --- Add other specific, trusted classes or packages needed by your Tasks below ---
          // Example: "com.mycompany.mywekatask.MyCustomTask"
      );

      System.out.println("DEBUG: Allowed serialization patterns:\n" + allowedPatterns.replace(';', '\n'));

      // Configure filter with pattern and limits
      ObjectInputFilter patternFilter = ObjectInputFilter.Config.createFilter(allowedPatterns);

      // Define resource limits
      long maxBytes = 20 * 1024 * 1024; // Max 20MB object graph size? Adjust.
      long maxDepth = 30;               // Max object nesting depth? Adjust.
      long maxRefs = 10000;             // Max total object references? Adjust.
      ObjectInputFilter limitFilter = ObjectInputFilter.Config.createFilter(
          String.format("maxbytes=%d;maxdepth=%d;maxrefs=%d", maxBytes, maxDepth, maxRefs)
      );

      // Combine the pattern filter and the limit filter
      ObjectInputFilter combinedFilter = ObjectInputFilter.merge(patternFilter, limitFilter);

      // Return a filter that logs rejections for debugging
      return (info) -> {
          ObjectInputFilter.Status status = combinedFilter.checkInput(info);
          if (status == ObjectInputFilter.Status.REJECTED) {
              System.err.println("Serialization Filter REJECTED: class="
                  + (info.serialClass() != null ? info.serialClass().getName() : "N/A")
                  + ", depth=" + info.depth() + ", refs=" + info.references()
                  + ", size=" + info.streamBytes() + ", arrayLength=" + info.arrayLength());
       }
          // Allow UNDECIDED to fall through (though our pattern should cover most)
          return status;
      };
  }


  /**
   * Main method: Sets up serialization filter, RMI environment, and starts the engine.
   *
   * @param args Command line arguments, "-p <port>" supported.
   */
  public static void main(String[] args) {

    // --- Step 1: Apply Global Serialization Filter EARLY ---
    // This is the most critical minimal security addition.
    try {
        ObjectInputFilter serialFilter = createRmiFilter();
        ObjectInputFilter.Config.setSerialFilter(serialFilter);
        System.err.println("INFO: Applied global RMI serialization filter.");
        // Note: Printing the filter object itself might not show the full pattern easily.
        System.err.println("      !! IMPORTANT: Test this filter thoroughly with your specific Weka tasks !!");
        System.err.println("      !!            Add necessary classes to createRmiFilter() if tasks fail !!");
    } catch (Exception | LinkageError e) { // Catch LinkageError too, in case filter API isn't available
         System.err.println("CRITICAL ERROR: Failed to set global serialization filter! RMI will be insecure. Exiting.");
         e.printStackTrace(System.err);
         System.exit(1);
    }
    // --- End Filter Setup ---


    // --- Step 2: Remove Deprecated Security Manager ---
    // The RMISecurityManager is deprecated and generally problematic.
    // System.setSecurityManager(new RMISecurityManager()); // <-- REMOVED
    System.err.println("INFO: RMISecurityManager is deprecated and NOT being used.");
    System.err.println("      Security relies primarily on the serialization filter.");
    // --- End Security Manager Removal ---


    // --- Step 3: Pre-load Weka classes (Optional but can help) ---
    try {
        System.err.println("Pre-loading Weka classes (may take a moment)...");
    weka.gui.GenericObjectEditor.determineClasses();
        System.err.println("Weka class pre-loading finished.");
    } catch (Exception e) {
        System.err.println("Warning: Error during Weka class pre-loading: " + e.getMessage());
        // Continue execution, but be aware tasks might fail later if classes aren't found
    }


    // --- Step 4: Configure RMI Port and Hostname ---
    int port = 1099; // Default RMI registry port
    String hostNameForBinding = "localhost"; // Default binding hostname

    try {
      // Attempt to get the local hostname for potentially more accessible binding
      InetAddress localhost = InetAddress.getLocalHost();
      hostNameForBinding = localhost.getHostName();
      System.err.println("INFO: Local Hostname determined as: " + hostNameForBinding);
    } catch (Exception ex) {
      System.err.println("Warning: Could not determine local hostname, using default '" + hostNameForBinding + "'. Error: " + ex.getMessage());
    }

    // Process port option from command line arguments
    try {
      String portOption = Utils.getOption("p", args);
      if (!portOption.isEmpty()) {
        try {
        port = Integer.parseInt(portOption);
            if (port <= 0 || port > 65535) {
                 System.err.println("Error: Invalid port number specified: " + port + ". Using default " + 1099 + ".");
                 port = 1099;
    } else {
                 System.err.println("INFO: Using specified RMI port: " + port);
            }
        } catch (NumberFormatException nfe) {
             System.err.println("Error: Invalid format for port number: '" + portOption + "'. Using default " + 1099 + ".");
             port = 1099;
        }
      } else {
           System.err.println("INFO: Using default RMI port: " + port);
      }
    } catch (Exception ex) {
        System.err.println("Error processing command line options: " + ex.getMessage());
        System.err.println("Usage hint: java weka.experiment.RemoteEngine [-p <port>]");
    }

    // Construct the RMI binding name (e.g., //myhost:1099/RemoteEngine)
    String rmiName = "//" + hostNameForBinding + ":" + port + "/RemoteEngine";
    System.err.println("INFO: Attempting to bind RemoteEngine to: " + rmiName);

    // --- Step 5: Add Explicit Security Warnings ---
    System.err.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
    System.err.println("!! SECURITY WARNING: RMI service starting with minimal security!");
    System.err.println("!! - NO client authentication implemented.");
    System.err.println("!! - NO transport encryption (TLS/SSL) enabled.");
    System.err.println("!! - Relies on serialization filter & network controls (firewall).");
    System.err.println("!! Ensure firewall rules restrict access to trusted clients ONLY.");
    System.err.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");


    // --- Step 6: Start RMI Service ---
    try {
      // Create the RemoteEngine instance
      Compute engine = new RemoteEngine(hostNameForBinding); // Pass hostname for internal reference

      try {
        // Try to rebind first (in case registry exists and object was bound before)
        Naming.rebind(rmiName, engine);
        System.out.println("INFO: RemoteEngine rebound in RMI registry: " + rmiName);
      } catch (RemoteException ex) {
        // If rebind fails, assume registry doesn't exist or isn't running
        System.err.println("INFO: RMI registry not found or rebind failed (" + ex.getMessage() + ").");
        // Attempt to create a local registry.
        // !! WARNING: Creating registry here might be insecure. External registry preferred. !!
        try {
            System.err.println("      Attempting to start local RMI registry on port " + port + "...");
        java.rmi.registry.LocateRegistry.createRegistry(port);
            System.err.println("      Local RMI registry created successfully.");
            // Now bind since the registry was just created
            Naming.bind(rmiName, engine);
            System.out.println("INFO: RemoteEngine bound in NEW RMI registry: " + rmiName);
        } catch (Exception e_reg) {
             // Catch specific exceptions if possible (e.g., AlreadyBoundException if race condition)
             System.err.println("CRITICAL ERROR: Failed to create or bind to local RMI registry: " + e_reg.getMessage());
             System.err.println("             Check if port " + port + " is in use or permissions are denied.");
             e_reg.printStackTrace(System.err);
             System.exit(1); // Exit if registry cannot be started/bound
  }
      }

      System.out.println("=======================================================");
      System.out.println(" RemoteEngine ("+ rmiName +") is ready.");
      System.out.println(" Waiting for tasks...");
      System.out.println("=======================================================");

    } catch (Exception e) { // Catch exceptions during RemoteEngine creation or initial export
      System.err.println("CRITICAL ERROR: Failed to start RemoteEngine service: " + e.getMessage());
      e.printStackTrace(System.err);
      System.exit(1); // Exit if the engine cannot be created/exported
  }
  } // End main
} // End class RemoteEngine

/*
```

**Summary of the "Lazy" Upgrade:**

1.  **Serialization Filter:** Added via `createRmiFilter()` and `ObjectInputFilter.Config.setSerialFilter()`. **You MUST test and likely customize the `allowedPatterns` string in `createRmiFilter()` for your specific Weka tasks.**
2.  **`RMISecurityManager` Removed:** The code setting the deprecated security manager is gone.
3.  **Warnings Added:** Clear warnings about remaining risks are printed on startup.

**Again, the Critical Caveats:**

* **Test the Filter:** The provided filter is a guess. Run your real workloads and check the server logs for "Serialization Filter REJECTED". Add any necessary Weka classes or your custom task classes to the `allowedPatterns` string in `createRmiFilter()`.
* **No Authentication:** Anyone who can reach the RMI port can send tasks.
* **No Encryption:** Data is sent unencrypted over the network.
* **Firewall is Essential:** You absolutely need a firewall to restrict access to this RMI port only from trusted client machines.

This is the simplest approach that addresses the most severe known vulnerability class in R
*/
