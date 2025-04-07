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
 *    BasicRLogger.java
 *    Copyright (C) 2022 University of Waikato, Hamilton, New Zealand
 *
 */

package weka.core;

/**
 * Basic logger for debugging purposes
 * 
 * @author Eibe Frank
 * @version $Revision:  $
 */
public class BasicRLogger implements RLoggerAPI {

  protected StringBuffer m_logBuffer = new StringBuffer();

  @Override
  public void statusMessage(String message) {
    m_logBuffer.append("STATUS UPDATE: " + message + "\n");
  }

  @Override
  public void logMessage(String message) {
    m_logBuffer.append(message + "\n");
  }

  /**
   * Get the current contents of the log buffer and then reset it.
   *
   * @return the current contents of the log buffer.
   */
  public String getLogBuffer() {
    String toReturn = m_logBuffer.toString();
    m_logBuffer.setLength(0);
    return toReturn;
  }
}
