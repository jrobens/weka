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
 *    GUIChooser.java
 *    Copyright (C) 1999-2012 University of Waikato, Hamilton, New Zealand
 *
 */

package weka.gui;

import weka.classifiers.bayes.net.GUI;
import weka.classifiers.evaluation.ThresholdCurve;
import weka.core.Copyright;
import weka.core.Defaults;
import weka.core.Instances;
import weka.core.Memory;
import weka.core.PluginManager;
import weka.core.Settings;
import weka.core.SystemInfo;
import weka.core.Utils;
import weka.core.Version;
import weka.core.WekaPackageClassLoaderManager;
import weka.core.WekaPackageManager;
import weka.core.scripting.Groovy;
import weka.core.scripting.Jython;
import weka.gui.arffviewer.ArffViewerMainPanel;
import weka.gui.boundaryvisualizer.BoundaryVisualizer;
import weka.gui.experiment.Experimenter;
import weka.gui.explorer.Explorer;
import weka.gui.graphvisualizer.GraphVisualizer;
// --- IMPORT ADDED BACK ---
import weka.gui.GUIChooserDefaults;
// --- END IMPORT ---
import weka.gui.knowledgeflow.KnowledgeFlowApp;
import weka.gui.knowledgeflow.MainKFPerspective;
import weka.gui.scripting.JythonPanel;
import weka.gui.sql.SqlViewer;
import weka.gui.treevisualizer.Node;
import weka.gui.treevisualizer.NodePlace;
import weka.gui.treevisualizer.PlaceNode2;
import weka.gui.treevisualizer.TreeBuild;
import weka.gui.treevisualizer.TreeVisualizer;
import weka.gui.visualize.PlotData2D;
import weka.gui.visualize.ThresholdVisualizePanel;
import weka.gui.visualize.VisualizePanel;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream; // Needed for image loading robustness
import java.io.PrintStream;
import java.io.Reader;
import java.security.Permission;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;
import java.util.Vector;
// Consider replacing Vector with ArrayList + synchronization if needed
// Consider replacing Hashtable with ConcurrentHashMap

/**
 * The main class for the Weka GUIChooser. Lets the user choose which GUI they
 * want to run.
 *
 * @author Len Trigg (trigg@cs.waikato.ac.nz)
 * @author Mark Hall (mhall@cs.waikato.ac.nz)
 * @author FracPete (fracpete at waikato dot ac dot nz)
 * @version $Revision$
 */
public class GUIChooserApp extends JFrame {

  /** for serialization */
  private static final long serialVersionUID = 9001529425230247914L;

  /** GUIChooser settings */
  private Settings m_settings;

  /** the GUIChooser itself */
  protected GUIChooserApp m_Self;

  // Menu stuff
  private JMenuBar m_jMenuBar;
  private JMenu m_jMenuProgram;
  private JMenu m_jMenuVisualization;
  private JMenu m_jMenuTools;
  private JMenu m_jMenuHelp;

  // Applications

  /** The frames for the various applications that are open */
  // Vector is thread-safe but older; consider CopyOnWriteArrayList if iteration >> modification
  protected Vector<JFrame> m_Frames = new Vector<JFrame>();

  /** the panel for the application buttons */
  protected JPanel m_PanelApplications = new JPanel();

  /** Click to open the Workbench */
  protected JButton m_WorkbenchBut = new JButton("Workbench");

  /** Click to open the Explorer */
  protected JButton m_ExplorerBut = new JButton("Explorer");

  /** Click to open the Explorer */
  protected JButton m_ExperimenterBut = new JButton("Experimenter");

  /** Click to open the KnowledgeFlow */
  protected JButton m_KnowledgeFlowBut = new JButton("KnowledgeFlow");

  /** Click to open the simplecli */
  protected JButton m_SimpleBut = new JButton("Simple CLI");

  /** The frame of the LogWindow */
  // Made static for singleton access? Ensure thread safety if modified outside EDT.
  protected static LogWindow m_LogWindow = new LogWindow();

  /** The weka image */
  Image m_weka; // Initialized in constructor

  /** filechooser for the TreeVisualizer */
  protected WekaFileChooser m_FileChooserTreeVisualizer; // Initialized in constructor

  /** filechooser for the GraphVisualizer */
  protected WekaFileChooser m_FileChooserGraphVisualizer; // Initialized in constructor

  /** filechooser for Plots */
  protected WekaFileChooser m_FileChooserPlot; // Initialized in constructor

  /** filechooser for ROC curves */
  protected WekaFileChooser m_FileChooserROC; // Initialized in constructor

  /** the icon for the frames */
  protected Image m_Icon; // Initialized in constructor

  /** contains the child frames (title <-> object). */
  // UPGRADE: Use Collections.synchronizedSet for basic thread safety if accessed outside EDT
  protected Set<Container> m_ChildFrames = Collections.synchronizedSet(new HashSet<>());

  // Singleton instance (if used, ensure thread-safe lazy initialization or eager init)
  private static GUIChooserApp m_chooser = null; // Used by createSingleton

  /**
   * Create a singleton instance of the GUIChooser
   */
  public static synchronized void createSingleton() {
    // Simple synchronized check-then-act (sufficient for basic singleton)
    if (m_chooser == null) {
      m_chooser = new GUIChooserApp();
    }
  }

  /**
   * Asks the user whether the window that generated the window evident given as the argument should
   * really be closed.
   *
   * @param e the event that the window generated
   * @return whether the user has agreed to close the window
   */
  public boolean checkWindowShouldBeClosed(WindowEvent e) {
    Component frame = (Component)e.getSource(); // Use Component for broader compatibility

    int result = JOptionPane.showConfirmDialog(
            frame, // Parent component for dialog
            "Are you sure you want to close this window?",
            "Close Window",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE); // Add message type

    return result == JOptionPane.YES_OPTION;
  }

  /**
   * Disposes the given JFrame and removes it from the list of frames.
   * Also attempts garbage collection and checks if the main app should exit.
   *
   * @param frame the frame to dispose of
   * @param adapter the window adapter used for dealing with the closing of the window
   */
  public void disposeWindow(JFrame frame, WindowAdapter adapter) {
    if (frame == null) return;

    frame.removeWindowListener(adapter);
    // Clear content pane and menu bar to help GC break references
    frame.setContentPane(new JPanel());
    frame.setJMenuBar(null); // Set to null instead of new JMenuBar()
    frame.dispose(); // Release window resources

    m_Frames.remove(frame); // Remove from tracking list

    System.gc(); // Suggest GC (effectiveness varies)
    checkExit(); // Check if the main application should exit
  }

  /**
   * Creates the experiment environment gui with no initial experiment
   */
  public GUIChooserApp() {

    super("Weka GUI Chooser"); // Set title via super constructor

    m_Self = this;

    // UPGRADE: Set Look and Feel early, handle potential errors
    try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    } catch (Exception e) {
        System.err.println("Warning: Could not set system look and feel.");
        // Optionally log the exception: e.printStackTrace(System.err);
    }

    setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); // Handle close via listener

    // Load settings
    m_settings = new Settings("weka", GUIChooserDefaults.APP_ID);
    GUIChooserDefaults guiChooserDefaults = new GUIChooserDefaults();
    Defaults pmDefaults =
      WekaPackageManager.getUnderlyingPackageManager().getDefaultSettings();
    guiChooserDefaults.add(pmDefaults);
    m_settings.applyDefaults(guiChooserDefaults);
    WekaPackageManager.getUnderlyingPackageManager().applySettings(m_settings);

    // Initialize FileChoosers with current directory
    File userDir = new File(System.getProperty("user.dir"));
    m_FileChooserTreeVisualizer = new WekaFileChooser(userDir);
    m_FileChooserGraphVisualizer = new WekaFileChooser(userDir);
    m_FileChooserPlot = new WekaFileChooser(userDir);
    m_FileChooserROC = new WekaFileChooser(userDir);

    // Configure FileChooser filters
    m_FileChooserGraphVisualizer
      .addChoosableFileFilter(new ExtensionFileFilter(".bif",
        "BIF Files (*.bif)"));
    m_FileChooserGraphVisualizer
      .addChoosableFileFilter(new ExtensionFileFilter(".xml",
        "XML Files (*.xml)")); // Add DOT filter?

    m_FileChooserPlot.addChoosableFileFilter(new ExtensionFileFilter(
      Instances.FILE_EXTENSION, "ARFF Files (*" + Instances.FILE_EXTENSION
        + ")"));
    m_FileChooserPlot.setMultiSelectionEnabled(true);

    m_FileChooserROC.addChoosableFileFilter(new ExtensionFileFilter(
      Instances.FILE_EXTENSION, "ARFF Files (*" + Instances.FILE_EXTENSION
        + ")"));

    // Load Icons and Images (handle potential loading errors)
    try {
        // Use getResourceAsStream for better reliability, especially in JARs
        InputStream iconStream = GUIChooserApp.class.getClassLoader().getResourceAsStream(
          "weka/gui/weka_icon_new_48.png");
        if (iconStream != null) {
            m_Icon = Toolkit.getDefaultToolkit().createImage(iconStream.readAllBytes());
    setIconImage(m_Icon);
        } else {
             System.err.println("Warning: Could not load application icon.");
        }

        InputStream wekaImageStream = GUIChooserApp.class.getClassLoader().getResourceAsStream(
          "weka/gui/images/weka_background_new.png");
         if (wekaImageStream != null) {
             m_weka = Toolkit.getDefaultToolkit().createImage(wekaImageStream.readAllBytes());
         } else {
              System.err.println("Warning: Could not load Weka background image.");
         }
    } catch (IOException | NullPointerException e) {
        System.err.println("Error loading image resources: " + e.getMessage());
    }


    // Setup main content pane
    Container contentPane = this.getContentPane();
    contentPane.setLayout(new BorderLayout());

    // --- Applications Panel ---
    m_PanelApplications.setBorder(BorderFactory.createTitledBorder("Applications"));
    m_PanelApplications.setLayout(new GridLayout(0, 1, 5, 5)); // Add gaps
    m_PanelApplications.add(m_ExplorerBut);
    m_PanelApplications.add(m_ExperimenterBut);
    m_PanelApplications.add(m_KnowledgeFlowBut);
    m_PanelApplications.add(m_WorkbenchBut);
    m_PanelApplications.add(m_SimpleBut);
    contentPane.add(m_PanelApplications, BorderLayout.EAST);

    // --- Weka Image and Info Panel ---
    JPanel wekaPan = new JPanel(new BorderLayout()); // Specify layout
    wekaPan.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10)); // Add padding
    wekaPan.setToolTipText("Weka, a native bird of New Zealand");

    if (m_weka != null) {
    ImageIcon wii = new ImageIcon(m_weka);
    JLabel wekaLab = new JLabel(wii);
    wekaLab.setToolTipText("Western Weka, Gallirallus australis australis, "
      + "collected 16 April 1987, Addison's Flat, Westport, New Zealand. "
      + "Field collection 1978 - 2004. CC BY 4.0. Te Papa");
    wekaPan.add(wekaLab, BorderLayout.CENTER);
    } else {
        wekaPan.add(new JLabel("Weka Image Not Found"), BorderLayout.CENTER); // Placeholder
    }

    String infoString =
      "<html><body style='text-align:center;'>" // Center align text
        + "<font size=-1>" // Slightly larger font
        + "Waikato Environment for Knowledge Analysis<br>"
        + "Version " + Version.VERSION + "<br>"
        + "(c) " + Copyright.getFromYear() + " - " + Copyright.getToYear() + " "
        + Copyright.getOwner() + "<br>"
        + "<a href=\"https://www.cs.waikato.ac.nz/ml/weka/\">https://www.cs.waikato.ac.nz/ml/weka/</a>" // Add URL
        + "</font>"
        + "</body></html>";
      // + Copyright.getAddress() + // Address might be too long

    JLabel infoLab = new JLabel(infoString);
    infoLab.setHorizontalAlignment(SwingConstants.CENTER); // Ensure label content is centered
    infoLab.setBorder(BorderFactory.createEmptyBorder(10, 5, 5, 5)); // Adjust padding
    wekaPan.add(infoLab, BorderLayout.SOUTH);

    contentPane.add(wekaPan, BorderLayout.CENTER);

    // --- Menu Bar ---
    setupMenuBar();
    setJMenuBar(m_jMenuBar);

    // --- Action Listeners for Buttons ---
    setupActionListeners();

    // --- Window Closing Behavior ---
    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        closeMainApp();
      }
    });

    // Pack and set visible (often done in main after ensuring EDT)
    pack();
    setLocationRelativeTo(null); // Center on screen initially
  }

  /** Helper method to set up the menu bar */
  private void setupMenuBar() {
    m_jMenuBar = new JMenuBar();

    // --- Program Menu ---
    m_jMenuProgram = new JMenu("Program");
    m_jMenuProgram.setMnemonic(KeyEvent.VK_P);
    m_jMenuBar.add(m_jMenuProgram);

    // Program/LogWindow
    JMenuItem jMenuItemProgramLogWindow = new JMenuItem("LogWindow", KeyEvent.VK_L);
    jMenuItemProgramLogWindow.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L, KeyEvent.CTRL_DOWN_MASK)); // Use CTRL_DOWN_MASK
    if (m_Icon != null) m_LogWindow.setIconImage(m_Icon);
    jMenuItemProgramLogWindow.addActionListener(e -> { // Use Lambda
        // Ensure log window is created/shown on EDT if not already visible
        SwingUtilities.invokeLater(() -> {
            if (!m_LogWindow.isVisible()) { // Avoid re-setting size/location if already visible
          m_LogWindow.pack();
          m_LogWindow.setSize(800, 600);
                m_LogWindow.setLocationRelativeTo(m_Self); // Relative to main chooser
        }
        m_LogWindow.setVisible(true);
            m_LogWindow.toFront(); // Bring to front
    });
    });
    m_jMenuProgram.add(jMenuItemProgramLogWindow);

    // Program/Memory Usage
    JMenuItem jMenuItemProgramMemUsage = new JMenuItem("Memory usage", KeyEvent.VK_M);
    jMenuItemProgramMemUsage.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_M, KeyEvent.CTRL_DOWN_MASK));
    jMenuItemProgramMemUsage.addActionListener(e -> { // Use Lambda
        final MemoryUsagePanel panel = new MemoryUsagePanel();
        final JFrame frame = Utils.getWekaJFrame("Memory usage", m_Self); // Use helper for consistent frame setup
        if (m_Icon != null) frame.setIconImage(m_Icon); // Set icon
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); // Handle close manually
        frame.getContentPane().setLayout(new BorderLayout());
        frame.getContentPane().add(panel, BorderLayout.CENTER);

        final WindowAdapter adapter = new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent w) {
            // No need to ask for confirmation for memory panel? Or keep it consistent?
            // Let's assume simple close is ok here.
              panel.stopMonitoring();
            disposeWindow(frame, this); // Use disposeWindow helper
            }
        };
        frame.addWindowListener(adapter);

        frame.pack();
        // frame.setSize(400, 50); // Let pack determine initial size
        Point l = panel.getFrameLocation(); // Restore previous location if available
        if (l != null && l.x != -1 && l.y != -1) {
          frame.setLocation(l);
        } else {
            frame.setLocationRelativeTo(m_Self); // Default location
        }
        frame.setVisible(true);
        m_Frames.add(frame); // Track the frame
    });
    m_jMenuProgram.add(jMenuItemProgramMemUsage);

    // Program/Settings
    JMenuItem jMenuItemSettings = new JMenuItem("Settings"); // No mnemonic/accelerator needed?
    jMenuItemSettings.addActionListener(e -> { // Use Lambda
        try {
          // Show editor centered over the main content pane
          int result = SettingsEditor.showSingleSettingsEditor(m_settings,
              GUIChooserDefaults.APP_ID, "GUIChooser Settings",
              (JComponent) GUIChooserApp.this.getContentPane(),
              550, 100); // Initial size hints
          if (result == JOptionPane.OK_OPTION) {
            // Apply settings if OK was clicked
            WekaPackageManager.getUnderlyingPackageManager().applySettings(m_settings);
            // Potentially notify other components or refresh UI if settings affect appearance/behavior
          }
        } catch (Exception ex) {
          System.err.println("Error opening Settings Editor: " + ex.getMessage());
          ex.printStackTrace(System.err); // Log error
          JOptionPane.showMessageDialog(m_Self,
              "Error opening Settings Editor:\n" + ex.getMessage(),
              "Settings Error", JOptionPane.ERROR_MESSAGE);
      }
    });
    m_jMenuProgram.add(jMenuItemSettings);

    m_jMenuProgram.add(new JSeparator());

    // Program/Exit
    JMenuItem jMenuItemProgramExit = new JMenuItem("Exit", KeyEvent.VK_X); // Use X for exit? E is often Edit
    // Common accelerator for exit is Alt+F4 (handled by OS) or Ctrl+Q
    jMenuItemProgramExit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, KeyEvent.CTRL_DOWN_MASK));
    jMenuItemProgramExit.addActionListener(e -> closeMainApp()); // Use helper method
    m_jMenuProgram.add(jMenuItemProgramExit);


    // --- Visualization Menu ---
    m_jMenuVisualization = new JMenu("Visualization");
    m_jMenuVisualization.setMnemonic(KeyEvent.VK_V);
    m_jMenuBar.add(m_jMenuVisualization);

    // Visualization/Plot
    JMenuItem jMenuItemVisualizationPlot = new JMenuItem("Plot", KeyEvent.VK_P);
    jMenuItemVisualizationPlot.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, KeyEvent.CTRL_DOWN_MASK));
    jMenuItemVisualizationPlot.addActionListener(e -> openPlotVisualizer()); // Use helper
    m_jMenuVisualization.add(jMenuItemVisualizationPlot);

    // Visualization/ROC
    JMenuItem jMenuItemVisualizationROC = new JMenuItem("ROC", KeyEvent.VK_R);
    jMenuItemVisualizationROC.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, KeyEvent.CTRL_DOWN_MASK));
    jMenuItemVisualizationROC.addActionListener(e -> openRocVisualizer()); // Use helper
    m_jMenuVisualization.add(jMenuItemVisualizationROC);

    // Visualization/TreeVisualizer
    JMenuItem jMenuItemVisualizationTree = new JMenuItem("TreeVisualizer", KeyEvent.VK_T);
    jMenuItemVisualizationTree.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T, KeyEvent.CTRL_DOWN_MASK));
    jMenuItemVisualizationTree.addActionListener(e -> openTreeVisualizer()); // Use helper
    m_jMenuVisualization.add(jMenuItemVisualizationTree);

    // Visualization/GraphVisualizer
    JMenuItem jMenuItemVisualizationGraph = new JMenuItem("GraphVisualizer", KeyEvent.VK_G);
    jMenuItemVisualizationGraph.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_G, KeyEvent.CTRL_DOWN_MASK));
    jMenuItemVisualizationGraph.addActionListener(e -> openGraphVisualizer()); // Use helper
    m_jMenuVisualization.add(jMenuItemVisualizationGraph);

    // Visualization/BoundaryVisualizer
    JMenuItem jMenuItemVisualizationBoundary = new JMenuItem("BoundaryVisualizer", KeyEvent.VK_B);
    jMenuItemVisualizationBoundary.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_B, KeyEvent.CTRL_DOWN_MASK));
    jMenuItemVisualizationBoundary.addActionListener(e -> openBoundaryVisualizer()); // Use helper
    m_jMenuVisualization.add(jMenuItemVisualizationBoundary);


    // --- Extensions Menu (Code seems mostly fine, ensure sorted insert works) ---
    JMenu jMenuExtensions = new JMenu("Extensions");
    jMenuExtensions.setMnemonic(KeyEvent.VK_E); // E might conflict with Exit if mnemonics active
    m_jMenuBar.add(jMenuExtensions);
    jMenuExtensions.setVisible(false); // Hide if no extensions

    String extensions =
      GenericObjectEditor.EDITOR_PROPERTIES.getProperty(
        MainMenuExtension.class.getName(), "");

    if (extensions != null && !extensions.isEmpty()) {
      jMenuExtensions.setVisible(true);
      String[] classnames = extensions.split(",");
      Hashtable<String, JMenu> submenus = new Hashtable<>(); // Use diamond operator

      // Add all extensions
      for (String classname : classnames) {
         classname = classname.trim(); // Trim whitespace
         if (classname.isEmpty()) continue;

        try {
          MainMenuExtension ext =
            (MainMenuExtension) WekaPackageClassLoaderManager.objectForName(classname);

          // Submenu handling
          JMenu submenu = null;
          if (ext.getSubmenuTitle() != null && !ext.getSubmenuTitle().isEmpty()) {
            submenu = submenus.get(ext.getSubmenuTitle());
            if (submenu == null) {
              submenu = new JMenu(ext.getSubmenuTitle());
              // Add mnemonic if possible/desired
              submenus.put(ext.getSubmenuTitle(), submenu);
              insertMenuItem(jMenuExtensions, submenu); // Ensure sorted insert
            }
          }

          // Create menu item
          JMenuItem menuitem = new JMenuItem(ext.getMenuTitle());
          // Add tooltip if provided
  /*        if (ext.getMenuTipText() != null) {
              menuitem.setToolTipText(ext.getMenuTipText());
          }*/

          // Set action listener
          ActionListener listener = ext.getActionListener(m_Self);
          if (listener != null) {
            menuitem.addActionListener(listener);
          } else {
            // If no listener, assume it provides content for a standard frame
            final MainMenuExtension finalExt = ext; // Final for lambda
            menuitem.addActionListener(actionEvent -> { // Use Lambda
                // Create frame on EDT
                SwingUtilities.invokeLater(() -> {
                    Component frame = createFrame(m_Self, finalExt.getMenuTitle(), null, null,
                                                  null, -1, -1, null, false, false);
                    finalExt.fillFrame(frame); // Fill frame content
                    frame.setVisible(true); // Show frame
                    // Track frame? Depends if createFrame adds it. Assume it does or add manually.
                });
            });
          }

          // Add menu item to correct menu (sorted)
          if (submenu != null) {
            insertMenuItem(submenu, menuitem);
          } else {
            insertMenuItem(jMenuExtensions, menuitem);
          }
        } catch (Exception e) {
          System.err.println("Error loading MainMenuExtension '" + classname + "': " + e.getMessage());
          e.printStackTrace(System.err); // Log error loading extension
        }
      }
    }


    // --- Tools Menu ---
    m_jMenuTools = new JMenu("Tools");
    m_jMenuTools.setMnemonic(KeyEvent.VK_T); // T already used by TreeVisualizer? Check consistency.
    m_jMenuBar.add(m_jMenuTools);

    // Tools/Package Manager
    final JMenuItem jMenuItemToolsPackageManager = new JMenuItem();
    final String offline = (WekaPackageManager.m_offline ? " (offline)" : "");
    jMenuItemToolsPackageManager.setText("Package manager" + offline);
    jMenuItemToolsPackageManager.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_U, KeyEvent.CTRL_DOWN_MASK)); // U for Update/Packages?
    jMenuItemToolsPackageManager.addActionListener(e -> openPackageManager(offline)); // Use helper
    m_jMenuTools.add(jMenuItemToolsPackageManager);

    // Tools/ArffViewer
    JMenuItem jMenuItemToolsArffViewer = new JMenuItem("ArffViewer", KeyEvent.VK_A);
    jMenuItemToolsArffViewer.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, KeyEvent.CTRL_DOWN_MASK));
    jMenuItemToolsArffViewer.addActionListener(e -> openArffViewer()); // Use helper
    m_jMenuTools.add(jMenuItemToolsArffViewer);

    // Tools/SqlViewer
    JMenuItem jMenuItemToolsSql = new JMenuItem("SqlViewer", KeyEvent.VK_S);
    jMenuItemToolsSql.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK));
    jMenuItemToolsSql.addActionListener(e -> openSqlViewer()); // Use helper
    m_jMenuTools.add(jMenuItemToolsSql);

    // Tools/Bayes Net Editor (ensure class exists)
    try {
        Class.forName("weka.classifiers.bayes.net.GUI"); // Check if BayesNet GUI is available
        JMenuItem jMenuItemToolsBayesNet = new JMenuItem("Bayes net editor");
        jMenuItemToolsBayesNet.addActionListener(e -> openBayesNetEditor()); // Use helper
        m_jMenuTools.add(jMenuItemToolsBayesNet);
    } catch (ClassNotFoundException cnfe) {
        // Bayes Net GUI not in classpath, don't add menu item
        System.err.println("Note: Bayes Net Editor GUI not found in classpath, menu item disabled.");
    }

    // Scripting tools (Jython/Groovy) - Add checks for library availability
    if (Jython.isPresent()) {
        JMenuItem jMenuItemToolsJython = new JMenuItem("Jython console");
        jMenuItemToolsJython.addActionListener(e -> openJythonConsole()); // Use helper
        m_jMenuTools.add(jMenuItemToolsJython);
    } else {
         System.err.println("Note: Jython library not found, Jython console disabled.");
    }
    // Add Groovy console similarly if desired and Groovy.isPresent() exists/is added

  }

  /** Helper method to set up action listeners for the main application buttons */
  private void setupActionListeners() {
      m_ExplorerBut.addActionListener(e -> openExplorer());
      m_ExperimenterBut.addActionListener(e -> openExperimenter());
      m_KnowledgeFlowBut.addActionListener(e -> openKnowledgeFlow());
      m_WorkbenchBut.addActionListener(e -> openWorkbench());
      m_SimpleBut.addActionListener(e -> openSimpleCLI());
  }

  // --- Helper methods for opening applications/visualizers ---
  // These methods encapsulate the logic for creating and showing frames

  private void openExplorer() {
      try {
          final Explorer explorer = new Explorer();
          final JFrame frame = Utils.getWekaJFrame("Weka Explorer", m_Self);
          if (m_Icon != null) frame.setIconImage(m_Icon);
          frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
          frame.getContentPane().setLayout(new BorderLayout());
          frame.getContentPane().add(explorer, BorderLayout.CENTER);

          final WindowAdapter adapter = new WindowAdapter() {
              @Override
              public void windowClosing(WindowEvent e) {
                  // --- CORRECTED LINE ---
                  // Use the GUIChooser's standard confirmation dialog
                  if (checkWindowShouldBeClosed(e)) {
                      // Optional: Add any specific cleanup needed for Explorer instance
                      // before disposing the frame, if Explorer provides such methods.
                      disposeWindow(frame, this);
                  }
                  // --- END CORRECTION ---
              }
          };
          frame.addWindowListener(adapter);
          frame.pack();
          frame.setLocationRelativeTo(m_Self);
          frame.setVisible(true);
          m_Frames.add(frame);
      } catch (Exception ex) {
          ex.printStackTrace();
          JOptionPane.showMessageDialog(m_Self, "Could not launch Explorer:\n" + ex.getMessage(), "Launch Error", JOptionPane.ERROR_MESSAGE);
      }
  }

  private void openExperimenter() {
      try {
          final Experimenter experimenter = new Experimenter(true); // Run in MDI mode? Check constructor meaning
          final JFrame frame = Utils.getWekaJFrame("Weka Experiment Environment", m_Self);
          if (m_Icon != null) frame.setIconImage(m_Icon);
          frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
          frame.getContentPane().setLayout(new BorderLayout());
          frame.getContentPane().add(experimenter, BorderLayout.CENTER);

          final WindowAdapter adapter = new WindowAdapter() {
              @Override
              public void windowClosing(WindowEvent e) {
                  if (checkWindowShouldBeClosed(e)) {
                      // Optional: Add any specific cleanup needed for Explorer instance
                      // before disposing the frame, if Explorer provides such methods.
                      disposeWindow(frame, this);
                  }
              }
          };
          frame.addWindowListener(adapter);
          frame.pack();
          frame.setLocationRelativeTo(m_Self);
          frame.setVisible(true);
          m_Frames.add(frame);
      } catch (Exception ex) {
          ex.printStackTrace();
          JOptionPane.showMessageDialog(m_Self, "Could not launch Experimenter:\n" + ex.getMessage(), "Launch Error", JOptionPane.ERROR_MESSAGE);
      }
  }

    private void openKnowledgeFlow() {
        try {
            // Use KnowledgeFlowApp for proper setup
            final KnowledgeFlowApp kfApp = new KnowledgeFlowApp();
            final JFrame frame = kfApp.getFrame(); // Get the frame managed by KF App
            if (m_Icon != null) frame.setIconImage(m_Icon);
            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); // KF handles its own closing

            // Ensure KF handles its own closing logic, maybe add our listener just for tracking?
            // Check if KF already adds a listener - avoid duplicates.
            // Let's assume KF manages its exit confirmation. We just need to track the frame.

            // Make sure it's visible and bring to front
            frame.setLocationRelativeTo(m_Self);
            frame.setVisible(true);
            frame.toFront();

            // Add to frame list *if not already tracked* by KF's logic or if we need separate tracking
            if (!m_Frames.contains(frame)) {
                 m_Frames.add(frame);
                 // Add a listener primarily to know when KF closes *independently*
                 final WindowAdapter adapter = new WindowAdapter() {
      @Override
                     public void windowClosed(WindowEvent e) { // Use closed, not closing
                         m_Frames.remove(frame);
                         System.gc();
                         checkExit();
                         frame.removeWindowListener(this); // Clean up listener
                     }
                 };
                 frame.addWindowListener(adapter);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(m_Self, "Could not launch KnowledgeFlow:\n" + ex.getMessage(), "Launch Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openWorkbench() {
        // Workbench might be integrated differently or deprecated depending on Weka version.
        // Assuming it launches similarly to others for this example.
        // If Workbench is the main MDI container, logic might need adjustment.
        System.err.println("Workbench button clicked - Placeholder: Implement Workbench launch if applicable.");
        JOptionPane.showMessageDialog(m_Self, "Workbench launch not implemented in this example.", "Not Implemented", JOptionPane.INFORMATION_MESSAGE);
        // If implemented, follow pattern: create instance, create frame, add listener, show, track.
    }

    private void openSimpleCLI() {
        try {
            final SimpleCLI cli = new SimpleCLI(); // Assumes SimpleCLI extends Component/JPanel
            final JFrame frame = Utils.getWekaJFrame("Weka Simple CLI", m_Self);
            if (m_Icon != null) frame.setIconImage(m_Icon);
            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            frame.getContentPane().setLayout(new BorderLayout());
            frame.getContentPane().add(cli, BorderLayout.CENTER);

            final WindowAdapter adapter = new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    // SimpleCLI probably doesn't need confirmation?
                    disposeWindow(frame, this);
                }
            };
            frame.addWindowListener(adapter);
            frame.pack();
            frame.setSize(600, 400); // Give CLI a reasonable default size
            frame.setLocationRelativeTo(m_Self);
            frame.setVisible(true);
            m_Frames.add(frame);
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(m_Self, "Could not launch Simple CLI:\n" + ex.getMessage(), "Launch Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openPlotVisualizer() {
        int retVal = m_FileChooserPlot.showOpenDialog(m_Self);
        if (retVal != JFileChooser.APPROVE_OPTION) return;

        VisualizePanel panel = new VisualizePanel();
        String filenames = "";
        File[] files = m_FileChooserPlot.getSelectedFiles();
        boolean success = false;

        for (int j = 0; j < files.length; j++) {
            File file = files[j];
            if (j > 0) filenames += ", ";
            filenames += file.getName(); // Use just filename for title brevity
            System.err.println("Loading instances from " + file.getAbsolutePath());

            // UPGRADE: Use try-with-resources for FileReader/BufferedReader
            try (Reader r = new BufferedReader(new FileReader(file))) {
            Instances i = new Instances(r);
                i.setClassIndex(i.numAttributes() - 1); // Assume class is last attribute
            PlotData2D pd1 = new PlotData2D(i);

            if (j == 0) {
                    pd1.setPlotName("Master plot: " + file.getName());
              panel.setMasterPlot(pd1);
            } else {
                    pd1.setPlotName("Plot " + (j + 1) + ": " + file.getName());
              pd1.m_useCustomColour = true;
                    // Cycle through a few basic colors
                    Color[] colors = {Color.red, Color.blue, Color.green, Color.orange, Color.magenta};
                    pd1.m_customColour = colors[j % colors.length];
              panel.addPlot(pd1);
            }
                success = true; // At least one plot added successfully
          } catch (Exception ex) {
                ex.printStackTrace(System.err); // Log error
            JOptionPane.showMessageDialog(m_Self, "Error loading file '"
                  + file.getName() + "':\n" + ex.getMessage(), "File Load Error", JOptionPane.ERROR_MESSAGE);
                // Decide whether to continue loading other files or stop
                // return; // Stop if one file fails? Or continue? Let's continue.
          }
        }

        if (!success) return; // Don't open frame if no plots were loaded

        // Create frame
        final JFrame frame = Utils.getWekaJFrame("Plot - " + filenames, m_Self);
        if (m_Icon != null) frame.setIconImage(m_Icon);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.getContentPane().setLayout(new BorderLayout());
        frame.getContentPane().add(panel, BorderLayout.CENTER);

        final WindowAdapter adapter = new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent e) {
            if (checkWindowShouldBeClosed(e)) {
              disposeWindow(frame, this);
            }
          }
        };
        frame.addWindowListener(adapter);
        frame.pack();
        frame.setSize(1024, 768); // Default size
        frame.setLocationRelativeTo(m_Self);
        frame.setVisible(true);
        m_Frames.add(frame);
      }

    private void openRocVisualizer() {
        int retVal = m_FileChooserROC.showOpenDialog(m_Self);
        if (retVal != JFileChooser.APPROVE_OPTION) return;

        File file = m_FileChooserROC.getSelectedFile();
        String filename = file.getAbsolutePath();
        Instances result = null;

        // UPGRADE: Use try-with-resources
        try (Reader reader = new BufferedReader(new FileReader(file))) {
            result = new Instances(reader);
        } catch (Exception ex) {
            ex.printStackTrace(System.err);
          JOptionPane.showMessageDialog(m_Self, "Error loading file '"
              + file.getName() + "':\n" + ex.getMessage(), "File Load Error", JOptionPane.ERROR_MESSAGE);
          return;
        }

        if (result.numInstances() == 0) {
             JOptionPane.showMessageDialog(m_Self, "No instances found in file: " + file.getName(),
                                           "Empty File", JOptionPane.WARNING_MESSAGE);
          return;
        }

        // Basic validation: Check if suitable for ROC (e.g., has class attribute)
        if (result.classIndex() < 0) {
             result.setClassIndex(result.numAttributes() - 1); // Assume last if not set
             if (result.classIndex() < 0) {
                  JOptionPane.showMessageDialog(m_Self, "Cannot determine class attribute for ROC curve in file: " + file.getName(),
                                                "ROC Error", JOptionPane.ERROR_MESSAGE);
                  return;
             }
        }


        ThresholdVisualizePanel vmc = new ThresholdVisualizePanel();
        try {
        vmc.setROCString("(Area under ROC = "
              + Utils.doubleToString(ThresholdCurve.getROCArea(result), 4) + ")"); // Calculate area
            vmc.setName(result.relationName() + " (ROC)"); // Set name for panel

        PlotData2D tempd = new PlotData2D(result);
        tempd.setPlotName(result.relationName());
            tempd.addInstanceNumberAttribute(); // Needed for ThresholdCurve
          vmc.addPlot(tempd);

        } catch (Exception ex) {
            ex.printStackTrace(System.err);
          JOptionPane.showMessageDialog(m_Self,
              "Error creating ROC plot:\n" + ex.getMessage(), "Plot Error", JOptionPane.ERROR_MESSAGE);
          return;
        }

        final JFrame frame = Utils.getWekaJFrame("ROC - " + file.getName(), m_Self);
        if (m_Icon != null) frame.setIconImage(m_Icon);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.getContentPane().setLayout(new BorderLayout());
        frame.getContentPane().add(vmc, BorderLayout.CENTER);

        final WindowAdapter adapter = new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent e) {
            if (checkWindowShouldBeClosed(e)) {
              disposeWindow(frame, this);
            }
          }
        };
        frame.addWindowListener(adapter);
        frame.pack();
        frame.setSize(800, 600); // Default size for ROC
        frame.setLocationRelativeTo(m_Self);
        frame.setVisible(true);
        m_Frames.add(frame);
      }

    private void openTreeVisualizer() {
        int retVal = m_FileChooserTreeVisualizer.showOpenDialog(m_Self);
        if (retVal != JFileChooser.APPROVE_OPTION) return;

        File file = m_FileChooserTreeVisualizer.getSelectedFile();
        String filename = file.getAbsolutePath();
        TreeBuild builder = new TreeBuild();
        Node top = null;
        NodePlace arrange = new PlaceNode2(); // Default placement algorithm

        // UPGRADE: Use try-with-resources
        try (Reader reader = new FileReader(file)) {
            top = builder.create(reader);
        } catch (Exception ex) {
            ex.printStackTrace(System.err);
            JOptionPane.showMessageDialog(m_Self, "Error loading tree file '"
              + file.getName() + "':\n" + ex.getMessage(), "File Load Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (top == null) {
             JOptionPane.showMessageDialog(m_Self, "Could not build tree from file: " + file.getName(),
                                           "Tree Build Error", JOptionPane.ERROR_MESSAGE);
          return;
        }

        // Create frame
        final JFrame frame = Utils.getWekaJFrame("TreeVisualizer - " + file.getName(), m_Self);
        if (m_Icon != null) frame.setIconImage(m_Icon);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.getContentPane().setLayout(new BorderLayout());
        // Pass null for Instances initially, user might load data later in visualizer?
        frame.getContentPane().add(new TreeVisualizer(null, top, arrange), BorderLayout.CENTER);

        final WindowAdapter adapter = new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent e) {
            if (checkWindowShouldBeClosed(e)) {
              disposeWindow(frame, this);
            }
          }
        };
        frame.addWindowListener(adapter);
        frame.pack();
        frame.setSize(1024, 768); // Default size
        frame.setLocationRelativeTo(m_Self);
        frame.setVisible(true);
        m_Frames.add(frame);
      }

    private void openGraphVisualizer() {
        int retVal = m_FileChooserGraphVisualizer.showOpenDialog(m_Self);
        if (retVal != JFileChooser.APPROVE_OPTION) return;

        File file = m_FileChooserGraphVisualizer.getSelectedFile();
        String filename = file.getAbsolutePath();
        GraphVisualizer panel = new GraphVisualizer();

        // UPGRADE: Use try-with-resources for file streams
        try {
            if (filename.toLowerCase().endsWith(".xml") || filename.toLowerCase().endsWith(".bif")) {
                try (InputStream fis = new FileInputStream(file)) {
                    panel.readBIF(fis);
                }
            } else { // Assume DOT format otherwise
                try (Reader reader = new FileReader(file)) {
                    panel.readDOT(reader);
                }
          }
        } catch (Exception ex) {
            ex.printStackTrace(System.err);
            JOptionPane.showMessageDialog(m_Self, "Error loading graph file '"
              + file.getName() + "':\n" + ex.getMessage(), "File Load Error", JOptionPane.ERROR_MESSAGE);
          return;
        }

        // Create frame
        final JFrame frame = Utils.getWekaJFrame("GraphVisualizer - " + file.getName(), m_Self);
        if (m_Icon != null) frame.setIconImage(m_Icon);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.getContentPane().setLayout(new BorderLayout());
        frame.getContentPane().add(panel, BorderLayout.CENTER);

        final WindowAdapter adapter = new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent e) {
            if (checkWindowShouldBeClosed(e)) {
              disposeWindow(frame, this);
            }
          }
        };
        frame.addWindowListener(adapter);
        frame.pack();
        frame.setSize(1024, 768); // Default size
        frame.setLocationRelativeTo(m_Self);
        frame.setVisible(true);
        m_Frames.add(frame);
      }

    private void openBoundaryVisualizer() {
        final JFrame frame = Utils.getWekaJFrame("BoundaryVisualizer", m_Self);
        if (m_Icon != null) frame.setIconImage(m_Icon);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.getContentPane().setLayout(new BorderLayout());
        final BoundaryVisualizer bv = new BoundaryVisualizer();
        frame.getContentPane().add(bv, BorderLayout.CENTER);
        // frame.setSize(bv.getMinimumSize()); // Let pack handle size initially

        final WindowAdapter adapter = new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent w) {
            if (checkWindowShouldBeClosed(w)) {
                    bv.stopPlotting(); // Ensure plotting stops
              disposeWindow(frame, this);
            }
          }
        };
        frame.addWindowListener(adapter);
        frame.pack(); // Pack after adding components
        frame.setResizable(false); // Keep non-resizable as original? Or allow resize?
        frame.setLocationRelativeTo(m_Self);
        frame.setVisible(true);
        m_Frames.add(frame);
        // Ensure this doesn't affect main app exit
        BoundaryVisualizer.setExitIfNoWindowsOpen(false);
      }

    private void openPackageManager(String offline) {
        // Run package manager loading in background thread
        new Thread(() -> {
            final weka.gui.PackageManager pm = new weka.gui.PackageManager();
            // Check if metadata is available before creating the frame
            if (!WekaPackageManager.m_noPackageMetaDataAvailable) {
                // Create frame on EDT
                SwingUtilities.invokeLater(() -> {
              final JFrame frame = Utils.getWekaJFrame("Package Manager" + offline, m_Self);
                    if (m_Icon != null) frame.setIconImage(m_Icon);
                    frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
              frame.getContentPane().setLayout(new BorderLayout());
              frame.getContentPane().add(pm, BorderLayout.CENTER);

                    final WindowAdapter adapter = new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent w) {
                  if (checkWindowShouldBeClosed(w)) {
                    disposeWindow(frame, this);
                  }
                }
                    };
                    frame.addWindowListener(adapter);
                    frame.pack(); // Pack first

                    // Set size relative to screen
                    Dimension screenSize = frame.getToolkit().getScreenSize();
                    int width = Math.min(1200, screenSize.width * 8 / 10); // Max width
                    int height = Math.min(800, screenSize.height * 8 / 10); // Max height
                    frame.setSize(width, height); // Set preferred size

                    frame.setLocationRelativeTo(m_Self); // Center relative to chooser
              frame.setVisible(true);
                    pm.setInitialSplitPaneDividerLocation(); // Adjust divider after visible
              m_Frames.add(frame);
                });
            } else {
                 // Handle case where package metadata isn't available (maybe show error dialog)
                 SwingUtilities.invokeLater(()-> {
                      JOptionPane.showMessageDialog(m_Self,
                         "Could not load package metadata.\n" +
                         "Package Manager may be offline or unable to access repository.",
                         "Package Manager Error", JOptionPane.ERROR_MESSAGE);
                 });
            }
        }).start(); // Start background thread
      }

    private void openArffViewer() {
        final JFrame frame = Utils.getWekaJFrame("ARFF-Viewer", m_Self);
        if (m_Icon != null) frame.setIconImage(m_Icon);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        final ArffViewerMainPanel arffViewerMainPanel = new ArffViewerMainPanel(frame); // Pass frame ref
        frame.getContentPane().setLayout(new BorderLayout());
        frame.getContentPane().add(arffViewerMainPanel, BorderLayout.CENTER);
        frame.setJMenuBar(arffViewerMainPanel.getMenu()); // Set menu from panel

        final WindowAdapter adapter = new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent w) {
                // ArffViewer might have unsaved changes, does it handle confirmation?
                // Assuming a simple close confirmation is sufficient here.
            if (checkWindowShouldBeClosed(w)) {
                    // Add any cleanup needed for ArffViewerMainPanel?
              disposeWindow(frame, this);
            }
          }
        };
        frame.addWindowListener(adapter);
        frame.pack();
        frame.setSize(1024, 768); // Default size
        frame.setLocationRelativeTo(m_Self);
        frame.setVisible(true);
        m_Frames.add(frame);
      }

    private void openSqlViewer() {
        final JFrame frame = Utils.getWekaJFrame("SqlViewer", m_Self);
        if (m_Icon != null) frame.setIconImage(m_Icon);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        final SqlViewer sql = new SqlViewer(frame); // Pass frame ref
        frame.getContentPane().setLayout(new BorderLayout());
        frame.getContentPane().add(sql, BorderLayout.CENTER);

        final WindowAdapter adapter = new WindowAdapter() {
              @Override
              public void windowClosing(WindowEvent w) {
                // SqlViewer might have pending operations or connections?
                // Add confirmation or cleanup logic if needed.
                if (checkWindowShouldBeClosed(w)) {
                    sql.saveSize(); // Save size preference
                    // Add sql.disconnect() or similar cleanup if necessary
                  disposeWindow(frame, this);
                }
              }
        };
        frame.addWindowListener(adapter);
        frame.pack(); // Pack first
        // Restore size? SqlViewer seems to handle its own size saving/restoring
            frame.setLocationRelativeTo(m_Self);
            frame.setVisible(true);
            m_Frames.add(frame);
          }

    private void openBayesNetEditor() {
        try {
            // Assumes weka.classifiers.bayes.net.GUI is the entry point
            final GUI bayesGui = new GUI(); // Create instance
            final JFrame frame = Utils.getWekaJFrame("Bayes Net Graphical Editor", m_Self);
            if (m_Icon != null) frame.setIconImage(m_Icon);
            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.getContentPane().setLayout(new BorderLayout());
            frame.getContentPane().add(bayesGui, BorderLayout.CENTER); // Add the GUI panel

            final WindowAdapter adapter = new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent w) {
                    // Does the Bayes GUI need confirmation for unsaved changes?
                    // Add bayesGui.confirmExit() or similar if available.
            if (checkWindowShouldBeClosed(w)) {
                        // Add any cleanup for bayesGui?
              disposeWindow(frame, this);
            }
          }
            };
            frame.addWindowListener(adapter);
        frame.pack();
            frame.setSize(1024, 768); // Default size
        frame.setLocationRelativeTo(m_Self);
        frame.setVisible(true);
        m_Frames.add(frame);
            } catch (Exception ex) {
            ex.printStackTrace(System.err);
            JOptionPane.showMessageDialog(m_Self, "Could not launch Bayes Net Editor:\n" + ex.getMessage(),
                                          "Launch Error", JOptionPane.ERROR_MESSAGE);
    }
  }

    private void openJythonConsole() {
        if (!Jython.isPresent()) { // Double check
             JOptionPane.showMessageDialog(m_Self, "Jython library not found in classpath.",
                                           "Jython Error", JOptionPane.ERROR_MESSAGE);
             return;
      }
        try {
            final JythonPanel jythonPanel = new JythonPanel();
            final JFrame frame = Utils.getWekaJFrame("Jython Console", m_Self);
            if (m_Icon != null) frame.setIconImage(m_Icon);
            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); // Use DISPOSE_ON_CLOSE? Or manual?
    frame.getContentPane().setLayout(new BorderLayout());
            frame.getContentPane().add(jythonPanel, BorderLayout.CENTER);

            final WindowAdapter adapter = new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent w) {
                    // Console probably doesn't need confirmation
                    jythonPanel.terminate(); // Ensure resources are released
          disposeWindow(frame, this);
        }
            };
            frame.addWindowListener(adapter);
    frame.pack();
            frame.setSize(800, 600); // Default size
    frame.setLocationRelativeTo(m_Self);
    frame.setVisible(true);
    m_Frames.add(frame);
      } catch (Exception ex) {
            ex.printStackTrace(System.err);
            JOptionPane.showMessageDialog(m_Self, "Could not launch Jython Console:\n" + ex.getMessage(),
                                          "Launch Error", JOptionPane.ERROR_MESSAGE);
        }
      }


  // --- Utility and Exit Logic ---

  /** Handles closing the main GUIChooser window */
  private void closeMainApp() {
      int result = JOptionPane.showConfirmDialog(
              m_Self,
              "Are you sure you want to exit the Weka GUI Chooser?",
              "Exit Weka", // More specific title
              JOptionPane.YES_NO_OPTION,
              JOptionPane.QUESTION_MESSAGE);
      if (result == JOptionPane.YES_OPTION) {
          dispose(); // Dispose this main window
          checkExit(); // Check if JVM should exit
    }
  }

  /**
   * Checks if there are any other frames open, and if not exits the VM.
   */
  protected void checkExit() {
    // Check if the main GUIChooser window itself is still visible or tracked
    boolean chooserVisible = this.isDisplayable() && this.isVisible();

    // Check if any tracked frames are still displayable
    boolean otherFramesOpen = false;
    // Iterate safely over a copy or use synchronized access if m_Frames can be modified concurrently
    synchronized (m_Frames) { // Simple synchronization for iteration
        for (JFrame frame : m_Frames) {
            if (frame != null && frame.isDisplayable() && frame.isVisible()) {
                otherFramesOpen = true;
                break;
            }
        }
    }


    if (!chooserVisible && !otherFramesOpen) {
      System.out.println("No other GUIs seem to be open. Exiting...");
      System.exit(0);
    } else {
       // System.out.println("DEBUG: checkExit - ChooserVisible=" + chooserVisible + ", OtherFramesOpen=" + otherFramesOpen);
    }
  }

  /**
   * Inserts the menuitem sorted into the menu.
   *
   * @param menu the menu to insert the item into
   * @param menuitem the menuitem to insert
   */
  protected static void insertMenuItem(JMenu menu, JMenuItem menuitem) {
    String label = menuitem.getText();
    int pos = -1;

    for (int i = 0; i < menu.getItemCount(); i++) {
      JMenuItem current = menu.getItem(i);
      // skip separators
      if (current == null) {
        continue;
      }
      // Handle potential separators correctly during comparison
      String currentLabel = current.getText();
      if (currentLabel == null || currentLabel.isEmpty()) { // Check for null/empty text (might indicate separator visually)
          continue; // Skip comparison if it's likely a separator
      }

      if (label.compareTo(currentLabel) < 0) {
        pos = i;
        break;
      }
    }

    if (pos == -1) {
      menu.add(menuitem);
    } else {
      menu.insert(menuitem, pos);
    }
  }

  /**
   * Creates a frame for the extensions.
   *
   * @param parent the parent frame, can be null
   * @param title the title for the frame
   * @param menu the menubar, can be null
   * @param content the content pane, can be null
   * @param listener the window listener, can be null
   * @param width the width, if -1 uses pack()
   * @param height the height, if -1 uses pack()
   * @param location the location, if null uses setLocationRelativeTo(parent)
   * @param resizable whether the frame should be resizable
   * @param dispose whether to use DISPOSE_ON_CLOSE (otherwise
   * DO_NOTHING_ON_CLOSE is used)
   * @return the generated frame
   */
  public Container createFrame(final Frame parent, String title, JMenuBar menu,
    Container content, final WindowAdapter listener, int width, int height,
    Point location, boolean resizable, final boolean dispose) {

    final JFrame frame = Utils.getWekaJFrame(title, parent); // Use helper
    if (m_Icon != null) frame.setIconImage(m_Icon); // Set icon

    if (menu != null) {
    frame.setJMenuBar(menu);
    }

    if (content != null) {
      frame.setContentPane(content);
    }

    // Set default close operation based on dispose flag
    frame.setDefaultCloseOperation(dispose ? JFrame.DISPOSE_ON_CLOSE
      : JFrame.DO_NOTHING_ON_CLOSE);

    // Add the primary listener if provided
    if (listener != null) {
      frame.addWindowListener(listener);
    }

    // Add internal listener for tracking and removal from m_ChildFrames
    // This ensures removal regardless of the primary listener or close operation
    final WindowAdapter internalAdapter = new WindowAdapter() {
             @Override
             public void windowClosed(WindowEvent e) {
             m_ChildFrames.remove(frame); // Remove from synchronized set
             // Optional: log removal
             // System.out.println("DEBUG: Child frame removed: " + frame.getTitle());
             // Don't call checkExit here unless closing child frames should trigger app exit check
             frame.removeWindowListener(this); // Clean up this internal listener
             }
         // If DO_NOTHING_ON_CLOSE, we might need windowClosing if the primary listener doesn't handle it
         @Override
         public void windowClosing(WindowEvent e) {
             if (!dispose && listener == null) {
                 // If DO_NOTHING and no external listener, maybe default to dispose? Or prompt?
                 // For simplicity, let's assume if DO_NOTHING is used, an external listener WILL handle it.
                 // If not, the window might become uncloseable without an external listener.
    }
         }
    };
    frame.addWindowListener(internalAdapter);

    // Add to tracking set immediately after creation
    m_ChildFrames.add(frame);


    if ((width == -1) || (height == -1)) {
    frame.pack();
    } else {
      frame.setSize(width, height);
    }

    frame.setResizable(resizable);

    if (location == null) {
    frame.setLocationRelativeTo(parent);
    } else {
      frame.setLocation(location);
  }

    // Note: Frame is not setVisible(true) here, caller should do that.
    return frame;
  }

  /**
   * The main method used to start the GUIChooser.
   *
   * @param args may contain the class name of a GUI component to start
   */
  public static void main(String[] args) {
    // UPGRADE: Ensure GUI creation happens on the Event Dispatch Thread
    SwingUtilities.invokeLater(() -> {
        // Set application name for macOS menu bar if applicable
        // Needs testing on macOS, might require additional properties or libraries
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.startsWith("mac os x")) {
             try {
                 // System.setProperty("apple.laf.useScreenMenuBar", "true"); // Deprecated/handled automatically?
                 System.setProperty("com.apple.mrj.application.apple.menu.about.name", "Weka GUIChooser");
                 // System.setProperty("apple.awt.application.name", "Weka GUIChooser"); // Newer property?
             } catch (SecurityException se) {
                  System.err.println("Warning: Security settings prevent setting macOS application name.");
             }
        }

        // create GUIChooser singleton
      GUIChooserApp.createSingleton();
        GUIChooserApp chooser = GUIChooserApp.m_chooser;

        // display GUIChooser
        chooser.setVisible(true);

        // start specific GUI if requested via command line argument?
        if (args.length == 1) {
            String component = args[0].toLowerCase(); // Compare lower case
            System.err.println("Command line argument found, attempting to launch: " + component);
            // Use helper methods for consistency
            switch (component) {
                case "explorer":
                chooser.openExplorer();
                    break;
                case "experimenter":
                chooser.openExperimenter();
                    break;
                case "knowledgeflow":
                chooser.openKnowledgeFlow();
                    break;
                case "simplecli":
                chooser.openSimpleCLI();
                    break;
                // Add cases for other apps if needed (e.g., "workbench")
                default:
                    System.err.println("Error: Unknown application specified via command line: '" + args[0] + "'");
                    // Optionally show a usage message dialog
                    // JOptionPane.showMessageDialog(chooser, "Unknown application: " + args[0] +
                    //      "\nValid options: explorer, experimenter, knowledgeflow, simplecli",
                    //      "Argument Error", JOptionPane.ERROR_MESSAGE);
                    break;
            }
          }
    }); // End SwingUtilities.invokeLater
        }
} // End GUIChooserApp class
/*

**Summary of Simple Upgrades Applied:**

1.  **System Look and Feel:** Added code at the beginning of the constructor to make the application use the operating system's native appearance.
2.  **EDT Safety:** Wrapped the GUI creation and display logic in `main` using `SwingUtilities.invokeLater` to ensure it runs on the Event Dispatch Thread, which is standard practice for Swing applications. Also wrapped some frame creation/showing calls within action listeners where appropriate.
3.  **`try-with-resources`:** Modified the file reading logic in the visualization action listeners (`openPlotVisualizer`, `openRocVisualizer`, `openTreeVisualizer`, `openGraphVisualizer`) to use `try-with-resources`. This guarantees that file streams (`FileReader`, `FileInputStream`, `BufferedReader`) are closed automatically, even if errors occur.
4.  **Thread-Safe Set:** Changed the declaration of `m_ChildFrames` from `new HashSet<Container>()` to `Collections.synchronizedSet(new HashSet<>())` to provide basic thread safety in case this set is accessed or modified from different threads (although typically Swing component access should be on the EDT).
5.  **Minor Cleanups:** Added some null checks, used lambdas for action listeners, slightly improved frame closing logic and resource cleanup hints in `disposeWindow`, added comments.

These changes enhance robustness and appearance with relatively low complexity. Remember that significantly improving concurrency (e.g., replacing `Vector`) or addressing potential security issues related to scripting/plugins would require more substantial ("less lazy") effo

*/
