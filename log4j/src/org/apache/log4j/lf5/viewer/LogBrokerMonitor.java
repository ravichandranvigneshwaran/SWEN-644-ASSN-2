/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.log4j.lf5.viewer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import org.apache.log4j.lf5.LogLevel;
import org.apache.log4j.lf5.LogRecord;
import org.apache.log4j.lf5.LogRecordFilter;
import org.apache.log4j.lf5.util.DateFormatManager;
import org.apache.log4j.lf5.util.LogFileParser;
import org.apache.log4j.lf5.viewer.categoryexplorer.CategoryExplorerTree;
import org.apache.log4j.lf5.viewer.categoryexplorer.CategoryPath;
import org.apache.log4j.lf5.viewer.configure.ConfigurationManager;
import org.apache.log4j.lf5.viewer.configure.MRUFileManager;

/**
 * LogBrokerMonitor
 *.
 * @author Michael J. Sikorsky
 * @author Robert Shaw
 * @author Brad Marlborough
 * @author Richard Wan
 * @author Brent Sprecher
 * @author Richard Hurst
 */

// Contributed by ThoughtWorks Inc.

public class LogBrokerMonitor {
  //--------------------------------------------------------------------------
  //   Constants:
  //--------------------------------------------------------------------------

  protected final LogBrokerMonitorR logBrokerMonitorR = new LogBrokerMonitorR();

  protected String _fontName = "Dialog";
  protected String _currentView = LogBrokerMonitorR.DETAILED_VIEW;

  protected boolean _loadSystemFonts = false;
  protected boolean _trackTableScrollPane = true;
  protected Dimension _lastTableViewportSize;
  protected boolean _callSystemExitOnClose = false;
  protected List _displayedLogBrokerProperties = new Vector();

  protected Map _logLevelMenuItems = new HashMap();
  protected Map _logTableColumnMenuItems = new HashMap();

  protected List _levels = null;
  protected List _columns = null;
  protected boolean _isDisposed = false;

  protected ConfigurationManager _configurationManager = null;
  protected MRUFileManager _mruFileManager = null;
  protected File _fileLocation = null;

  //--------------------------------------------------------------------------
  //   Private Variables:
  //--------------------------------------------------------------------------

  //--------------------------------------------------------------------------
  //   Constructors:
  //--------------------------------------------------------------------------

  /**
   * Construct a LogBrokerMonitor.
   */
  public LogBrokerMonitor(List logLevels) {

    _levels = logLevels;
    _columns = LogTableColumn.getLogTableColumns();
    // This allows us to use the LogBroker in command line tools and
    // have the option for it to shutdown.

    String callSystemExitOnClose =
        System.getProperty("monitor.exit");
    if (callSystemExitOnClose == null) {
      callSystemExitOnClose = "false";
    }
    callSystemExitOnClose = callSystemExitOnClose.trim().toLowerCase();

    if (callSystemExitOnClose.equals("true")) {
      _callSystemExitOnClose = true;
    }

    initComponents();


    logBrokerMonitorR.get_logMonitorFrame().addWindowListener(
        new LogBrokerMonitorWindowAdaptor(this));

  }

  //--------------------------------------------------------------------------
  //   Public Methods:
  //--------------------------------------------------------------------------

  /**
   * Show the frame for the LogBrokerMonitor. Dispatched to the
   * swing thread.
   */
  public void show(final int delay) {
    if (logBrokerMonitorR.get_logMonitorFrame().isVisible()) {
      return;
    }
    // This request is very low priority, let other threads execute first.
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        Thread.yield();
        pause(delay);
        logBrokerMonitorR.get_logMonitorFrame().setVisible(true);
      }
    });
  }

  public void show() {
    show(0);
  }

  /**
   * Dispose of the frame for the LogBrokerMonitor.
   */
  public void dispose() {
    logBrokerMonitorR.get_logMonitorFrame().dispose();
    _isDisposed = true;

    if (_callSystemExitOnClose == true) {
      System.exit(0);
    }
  }

  /**
   * Hide the frame for the LogBrokerMonitor.
   */
  public void hide() {
    logBrokerMonitorR.get_logMonitorFrame().setVisible(false);
  }

  /**
   * Get the DateFormatManager for formatting dates.
   */
  public DateFormatManager getDateFormatManager() {
    return logBrokerMonitorR.get_table().getDateFormatManager();
  }

  /**
   * Set the date format manager for formatting dates.
   */
  public void setDateFormatManager(DateFormatManager dfm) {
    logBrokerMonitorR.get_table().setDateFormatManager(dfm);
  }

  /**
   * Get the value of whether or not System.exit() will be called
   * when the LogBrokerMonitor is closed.
   */
  public boolean getCallSystemExitOnClose() {
    return _callSystemExitOnClose;
  }

  /**
   * Set the value of whether or not System.exit() will be called
   * when the LogBrokerMonitor is closed.
   */
  public void setCallSystemExitOnClose(boolean callSystemExitOnClose) {
    _callSystemExitOnClose = callSystemExitOnClose;
  }

  /**
   * Add a log record message to be displayed in the LogTable.
   * This method is thread-safe as it posts requests to the SwingThread
   * rather than processing directly.
   */
  public void addMessage(final LogRecord lr) {
    if (_isDisposed == true) {
      // If the frame has been disposed of, do not log any more
      // messages.
      return;
    }

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        logBrokerMonitorR.get_categoryExplorerTree().getExplorerModel().addLogRecord(lr);
        logBrokerMonitorR.get_table().getFilteredLogTableModel().addLogRecord(lr); // update table
        logBrokerMonitorR.updateStatusLabel(); // show updated counts
      }
    });
  }

  public void setMaxNumberOfLogRecords(int maxNumberOfLogRecords) {
    logBrokerMonitorR.get_table().getFilteredLogTableModel().setMaxNumberOfLogRecords(maxNumberOfLogRecords);
  }

  public JFrame getBaseFrame() {
    return logBrokerMonitorR.get_logMonitorFrame();
  }

  public void setTitle(String title) {
    logBrokerMonitorR.get_logMonitorFrame().setTitle(title + " - LogFactor5");
  }

  public void setFrameSize(int width, int height) {
    Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
    if (0 < width && width < screen.width) {
      logBrokerMonitorR.set_logMonitorFrameWidth(width);
    }
    if (0 < height && height < screen.height) {
      logBrokerMonitorR.set_logMonitorFrameHeight(height);
    }
    logBrokerMonitorR.updateFrameSize();
  }

  public void setFontSize(int fontSize) {
    logBrokerMonitorR.changeFontSizeCombo(logBrokerMonitorR.get_fontSizeCombo(), fontSize);
    // setFontSizeSilently(actualFontSize); - changeFontSizeCombo fires event
    // refreshDetailTextArea();
  }

  public void addDisplayedProperty(Object messageLine) {
    _displayedLogBrokerProperties.add(messageLine);
  }

  public Map getLogLevelMenuItems() {
    return _logLevelMenuItems;
  }

  public Map getLogTableColumnMenuItems() {
    return _logTableColumnMenuItems;
  }

  public JCheckBoxMenuItem getTableColumnMenuItem(LogTableColumn column) {
    return getLogTableColumnMenuItem(column);
  }

  public CategoryExplorerTree getCategoryExplorerTree() {
    return logBrokerMonitorR.get_categoryExplorerTree();
  }

  // Added in version 1.2 - gets the value of the NDC text filter
  // This value is set back to null each time the Monitor is initialized.
  public String getNDCTextFilter() {
    return logBrokerMonitorR.get_NDCTextFilter();
  }

  // Added in version 1.2 - sets the NDC Filter based on
  // a String passed in by the user.  This value is persisted
  // in the XML Configuration file.
  public void setNDCLogRecordFilter(String textFilter) {
    logBrokerMonitorR.get_table().getFilteredLogTableModel().
        setLogRecordFilter(createNDCLogRecordFilter(textFilter));
  }
  //--------------------------------------------------------------------------
  //   Protected Methods:
  //--------------------------------------------------------------------------

  protected void setSearchText(String text) {
    logBrokerMonitorR.set_searchText(text);
  }

  // Added in version 1.2 - Sets the text filter for the NDC
  protected void setNDCTextFilter(String text) {
    // if no value is set, set it to a blank string
    // otherwise use the value provided
    if (text == null) {
      logBrokerMonitorR.set_NDCTextFilter("");
    } else {
      logBrokerMonitorR.set_NDCTextFilter(text);
    }
  }

  // Added in version 1.2 - Uses a different filter that sorts
  // based on an NDC string passed in by the user.  If the string
  // is null or is an empty string, we do nothing.
  protected void sortByNDC() {
    String text = logBrokerMonitorR.get_NDCTextFilter();
    if (text == null || text.length() == 0) {
      return;
    }

    // Use new NDC filter
    logBrokerMonitorR.get_table().getFilteredLogTableModel().
        setLogRecordFilter(createNDCLogRecordFilter(text));
  }

  protected void findSearchText() {
    String text = logBrokerMonitorR.get_searchText();
    if (text == null || text.length() == 0) {
      return;
    }
    int startRow = getFirstSelectedRow();
    int foundRow = findRecord(
        startRow,
        text,
            logBrokerMonitorR.get_table().getFilteredLogTableModel().getFilteredRecords()
    );
    selectRow(foundRow);
  }

  protected int getFirstSelectedRow() {
    return logBrokerMonitorR.get_table().getSelectionModel().getMinSelectionIndex();
  }

  protected void selectRow(int foundRow) {
    if (foundRow == -1) {
      String message = logBrokerMonitorR.get_searchText() + " not found.";
      JOptionPane.showMessageDialog(
              logBrokerMonitorR.get_logMonitorFrame(),
          message,
          "Text not found",
          JOptionPane.INFORMATION_MESSAGE
      );
      return;
    }
    LF5SwingUtils.selectRow(foundRow, logBrokerMonitorR.get_table(), logBrokerMonitorR.get_logTableScrollPane());
  }

  protected int findRecord(
      int startRow,
      String searchText,
      List records
      ) {
    if (startRow < 0) {
      startRow = 0; // start at first element if no rows are selected
    } else {
      startRow++; // start after the first selected row
    }
    int len = records.size();

    for (int i = startRow; i < len; i++) {
      if (matches((LogRecord) records.get(i), searchText)) {
        return i; // found a record
      }
    }
    // wrap around to beginning if when we reach the end with no match
    len = startRow;
    for (int i = 0; i < len; i++) {
      if (matches((LogRecord) records.get(i), searchText)) {
        return i; // found a record
      }
    }
    // nothing found
    return -1;
  }

  /**
   * Check to see if the any records contain the search string.
   * Searching now supports NDC messages and date.
   */
  protected boolean matches(LogRecord record, String text) {
    String message = record.getMessage();
    String NDC = record.getNDC();

    if (message == null && NDC == null || text == null) {
      return false;
    }
    if (message.toLowerCase().indexOf(text.toLowerCase()) == -1 &&
        NDC.toLowerCase().indexOf(text.toLowerCase()) == -1) {
      return false;
    }

    return true;
  }

  /**
   * When the fontsize of a JTextArea is changed, the word-wrapped lines
   * may become garbled.  This method clears and resets the text of the
   * text area.
   */
  protected void refresh(JTextArea textArea) {
    String text = textArea.getText();
    textArea.setText("");
    textArea.setText(text);
  }

  protected void refreshDetailTextArea() {
    refresh(logBrokerMonitorR.get_table()._detailTextArea);
  }

  protected void clearDetailTextArea() {
    logBrokerMonitorR.get_table()._detailTextArea.setText("");
  }

  /**
   * Changes the font selection in the combo box and returns the
   * size actually selected.
   * @return -1 if unable to select an appropriate font
   */
  protected int changeFontSizeCombo(JComboBox box, int requestedSize) {
    return logBrokerMonitorR.changeFontSizeCombo(box, requestedSize);
  }

  /**
   * Does not update gui or cause any events to be fired.
   */
  protected void setFontSizeSilently(int fontSize) {
    logBrokerMonitorR.set_fontSize(fontSize);
    logBrokerMonitorR.setFontSize(logBrokerMonitorR.get_table()._detailTextArea, fontSize);
    selectRow(0);
    logBrokerMonitorR.setFontSize(logBrokerMonitorR.get_table(), fontSize);
  }

  protected void setFontSize(Component component, int fontSize) {
    logBrokerMonitorR.setFontSize(component, fontSize);
  }

  protected void updateFrameSize() {
    logBrokerMonitorR.updateFrameSize();
  }

  protected void pause(int millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {

    }
  }

  protected void initComponents() {
    //
    // Configure the Frame.
    //
    logBrokerMonitorR.set_logMonitorFrame(new JFrame("LogFactor5"));

    logBrokerMonitorR.get_logMonitorFrame().setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

    String resource =
        "/org/apache/log4j/lf5/viewer/images/lf5_small_icon.gif";
    URL lf5IconURL = getClass().getResource(resource);

    if (lf5IconURL != null) {
      logBrokerMonitorR.get_logMonitorFrame().setIconImage(new ImageIcon(lf5IconURL).getImage());
    }
    logBrokerMonitorR.updateFrameSize();

    //
    // Configure the LogTable.
    //
    JTextArea detailTA = logBrokerMonitorR.createDetailTextArea();
    JScrollPane detailTAScrollPane = new JScrollPane(detailTA);
    logBrokerMonitorR.set_table(new LogTable(detailTA));
    setView(_currentView, logBrokerMonitorR.get_table());
    logBrokerMonitorR.get_table().setFont(new Font(_fontName, Font.PLAIN, logBrokerMonitorR.get_fontSize()));
    logBrokerMonitorR.set_logTableScrollPane(new JScrollPane(logBrokerMonitorR.get_table()));

    if (_trackTableScrollPane) {
      logBrokerMonitorR.get_logTableScrollPane().getVerticalScrollBar().addAdjustmentListener(
          new TrackingAdjustmentListener()
      );
    }


    // Configure the SplitPane between the LogTable & DetailTextArea
    //

    JSplitPane tableViewerSplitPane = new JSplitPane();
    tableViewerSplitPane.setOneTouchExpandable(true);
    tableViewerSplitPane.setOrientation(JSplitPane.VERTICAL_SPLIT);
    tableViewerSplitPane.setLeftComponent(logBrokerMonitorR.get_logTableScrollPane());
    tableViewerSplitPane.setRightComponent(detailTAScrollPane);
    // Make sure to do this last..
    //tableViewerSplitPane.setDividerLocation(1.0); Doesn't work
    //the same under 1.2.x & 1.3
    // "350" is a magic number that provides the correct default
    // behaviour under 1.2.x & 1.3.  For example, bumping this
    // number to 400, causes the pane to be completely open in 1.2.x
    // and closed in 1.3
    tableViewerSplitPane.setDividerLocation(350);

    //
    // Configure the CategoryExplorer
    //

    logBrokerMonitorR.set_categoryExplorerTree(new CategoryExplorerTree());

    logBrokerMonitorR.get_table().getFilteredLogTableModel().setLogRecordFilter(createLogRecordFilter());

    JScrollPane categoryExplorerTreeScrollPane =
        new JScrollPane(logBrokerMonitorR.get_categoryExplorerTree());
    categoryExplorerTreeScrollPane.setPreferredSize(new Dimension(130, 400));

    // Load most recently used file list
    _mruFileManager = new MRUFileManager();

    //
    // Configure the SplitPane between the CategoryExplorer & (LogTable/Detail)
    //

    JSplitPane splitPane = new JSplitPane();
    splitPane.setOneTouchExpandable(true);
    splitPane.setRightComponent(tableViewerSplitPane);
    splitPane.setLeftComponent(categoryExplorerTreeScrollPane);
    // Do this last.
    splitPane.setDividerLocation(130);
    //
    // Add the MenuBar, StatusArea, CategoryExplorer|LogTable to the
    // LogMonitorFrame.
    //
    logBrokerMonitorR.get_logMonitorFrame().getRootPane().setJMenuBar(createMenuBar());
    logBrokerMonitorR.get_logMonitorFrame().getContentPane().add(splitPane, BorderLayout.CENTER);
    logBrokerMonitorR.get_logMonitorFrame().getContentPane().add(createToolBar(),
        BorderLayout.NORTH);
    logBrokerMonitorR.get_logMonitorFrame().getContentPane().add(logBrokerMonitorR.createStatusArea(),
        BorderLayout.SOUTH);

    makeLogTableListenToCategoryExplorer();
    addTableModelProperties();

    //
    // Configure ConfigurationManager
    //
    _configurationManager = new ConfigurationManager(this, logBrokerMonitorR.get_table());

  }

  protected LogRecordFilter createLogRecordFilter() {
    LogRecordFilter result = new LogRecordFilter() {
      public boolean passes(LogRecord record) {
        CategoryPath path = new CategoryPath(record.getCategory());
        return
            getMenuItem(record.getLevel()).isSelected() &&
                    logBrokerMonitorR.get_categoryExplorerTree().getExplorerModel().isCategoryPathActive(path);
      }
    };
    return result;
  }

  // Added in version 1.2 - Creates a new filter that sorts records based on
  // an NDC string passed in by the user.
  protected LogRecordFilter createNDCLogRecordFilter(String text) {
    logBrokerMonitorR.set_NDCTextFilter(text);
    LogRecordFilter result = new LogRecordFilter() {
      public boolean passes(LogRecord record) {
        String NDC = record.getNDC();
        CategoryPath path = new CategoryPath(record.getCategory());
        if (NDC == null || logBrokerMonitorR.get_NDCTextFilter() == null) {
          return false;
        } else if (NDC.toLowerCase().indexOf(logBrokerMonitorR.get_NDCTextFilter().toLowerCase()) == -1) {
          return false;
        } else {
          return getMenuItem(record.getLevel()).isSelected() &&
                  logBrokerMonitorR.get_categoryExplorerTree().getExplorerModel().isCategoryPathActive(path);
        }
      }
    };

    return result;
  }


  protected void updateStatusLabel() {
    logBrokerMonitorR.updateStatusLabel();
  }

  protected String getRecordsDisplayedMessage() {
    return logBrokerMonitorR.getRecordsDisplayedMessage();
  }

  protected void addTableModelProperties() {
    final FilteredLogTableModel model = logBrokerMonitorR.get_table().getFilteredLogTableModel();

    addDisplayedProperty(new Object() {
      public String toString() {
        return logBrokerMonitorR.getRecordsDisplayedMessage();
      }
    });
    addDisplayedProperty(new Object() {
      public String toString() {
        return "Maximum number of displayed LogRecords: "
            + model._maxNumberOfLogRecords;
      }
    });
  }

  protected String getStatusText(int displayedRows, int totalRows) {
    return logBrokerMonitorR.getStatusText(displayedRows, totalRows);
  }

  protected void makeLogTableListenToCategoryExplorer() {
    ActionListener listener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        logBrokerMonitorR.get_table().getFilteredLogTableModel().refresh();
        logBrokerMonitorR.updateStatusLabel();
      }
    };
    logBrokerMonitorR.get_categoryExplorerTree().getExplorerModel().addActionListener(listener);
  }

  protected JPanel createStatusArea() {

    return logBrokerMonitorR.createStatusArea();
  }

  protected JTextArea createDetailTextArea() {
    return logBrokerMonitorR.createDetailTextArea();
  }

  protected JMenuBar createMenuBar() {
    JMenuBar menuBar = new JMenuBar();
    menuBar.add(createFileMenu());
    menuBar.add(createEditMenu());
    menuBar.add(createLogLevelMenu());
    menuBar.add(createViewMenu());
    menuBar.add(createConfigureMenu());
    menuBar.add(createHelpMenu());

    return (menuBar);
  }

  protected JMenu createLogLevelMenu() {
    JMenu result = new JMenu("Log Level");
    result.setMnemonic('l');
    Iterator levels = getLogLevels();
    while (levels.hasNext()) {
      result.add(getMenuItem((LogLevel) levels.next()));
    }

    result.addSeparator();
    result.add(createAllLogLevelsMenuItem());
    result.add(createNoLogLevelsMenuItem());
    result.addSeparator();
    result.add(createLogLevelColorMenu());
    result.add(createResetLogLevelColorMenuItem());

    return result;
  }

  protected JMenuItem createAllLogLevelsMenuItem() {
    JMenuItem result = new JMenuItem("Show all LogLevels");
    result.setMnemonic('s');
    result.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        selectAllLogLevels(true);
        logBrokerMonitorR.get_table().getFilteredLogTableModel().refresh();
        logBrokerMonitorR.updateStatusLabel();
      }
    });
    return result;
  }

  protected JMenuItem createNoLogLevelsMenuItem() {
    JMenuItem result = new JMenuItem("Hide all LogLevels");
    result.setMnemonic('h');
    result.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        selectAllLogLevels(false);
        logBrokerMonitorR.get_table().getFilteredLogTableModel().refresh();
        logBrokerMonitorR.updateStatusLabel();
      }
    });
    return result;
  }

  protected JMenu createLogLevelColorMenu() {
    JMenu colorMenu = new JMenu("Configure LogLevel Colors");
    colorMenu.setMnemonic('c');
    Iterator levels = getLogLevels();
    while (levels.hasNext()) {
      colorMenu.add(createSubMenuItem((LogLevel) levels.next()));
    }

    return colorMenu;
  }

  protected JMenuItem createResetLogLevelColorMenuItem() {
    JMenuItem result = new JMenuItem("Reset LogLevel Colors");
    result.setMnemonic('r');
    result.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        // reset the level colors in the map
        LogLevel.resetLogLevelColorMap();

        // refresh the table
        logBrokerMonitorR.get_table().getFilteredLogTableModel().refresh();
      }
    });
    return result;
  }

  protected void selectAllLogLevels(boolean selected) {
    Iterator levels = getLogLevels();
    while (levels.hasNext()) {
      getMenuItem((LogLevel) levels.next()).setSelected(selected);
    }
  }

  protected JCheckBoxMenuItem getMenuItem(LogLevel level) {
    JCheckBoxMenuItem result = (JCheckBoxMenuItem) (_logLevelMenuItems.get(level));
    if (result == null) {
      result = createMenuItem(level);
      _logLevelMenuItems.put(level, result);
    }
    return result;
  }

  protected JMenuItem createSubMenuItem(LogLevel level) {
    final JMenuItem result = new JMenuItem(level.toString());
    final LogLevel logLevel = level;
    result.setMnemonic(level.toString().charAt(0));
    result.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        showLogLevelColorChangeDialog(result, logLevel);
      }
    });

    return result;

  }

  protected void showLogLevelColorChangeDialog(JMenuItem result, LogLevel level) {
    JMenuItem menuItem = result;
    Color newColor = JColorChooser.showDialog(
            logBrokerMonitorR.get_logMonitorFrame(),
        "Choose LogLevel Color",
        result.getForeground());

    if (newColor != null) {
      // set the color for the record
      level.setLogLevelColorMap(level, newColor);
      logBrokerMonitorR.get_table().getFilteredLogTableModel().refresh();
    }

  }

  protected JCheckBoxMenuItem createMenuItem(LogLevel level) {
    JCheckBoxMenuItem result = new JCheckBoxMenuItem(level.toString());
    result.setSelected(true);
    result.setMnemonic(level.toString().charAt(0));
    result.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        logBrokerMonitorR.get_table().getFilteredLogTableModel().refresh();
        logBrokerMonitorR.updateStatusLabel();
      }
    });
    return result;
  }

  // view menu
  protected JMenu createViewMenu() {
    JMenu result = new JMenu("View");
    result.setMnemonic('v');
    Iterator columns = getLogTableColumns();
    while (columns.hasNext()) {
      result.add(getLogTableColumnMenuItem((LogTableColumn) columns.next()));
    }

    result.addSeparator();
    result.add(createAllLogTableColumnsMenuItem());
    result.add(createNoLogTableColumnsMenuItem());
    return result;
  }

  protected JCheckBoxMenuItem getLogTableColumnMenuItem(LogTableColumn column) {
    JCheckBoxMenuItem result = (JCheckBoxMenuItem) (_logTableColumnMenuItems.get(column));
    if (result == null) {
      result = createLogTableColumnMenuItem(column);
      _logTableColumnMenuItems.put(column, result);
    }
    return result;
  }

  protected JCheckBoxMenuItem createLogTableColumnMenuItem(LogTableColumn column) {
    JCheckBoxMenuItem result = new JCheckBoxMenuItem(column.toString());

    result.setSelected(true);
    result.setMnemonic(column.toString().charAt(0));
    result.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        // update list of columns and reset the view
        List selectedColumns = updateView();
        logBrokerMonitorR.get_table().setView(selectedColumns);
      }
    });
    return result;
  }

  protected List updateView() {
    ArrayList updatedList = new ArrayList();
    Iterator columnIterator = _columns.iterator();
    while (columnIterator.hasNext()) {
      LogTableColumn column = (LogTableColumn) columnIterator.next();
      JCheckBoxMenuItem result = getLogTableColumnMenuItem(column);
      // check and see if the checkbox is checked
      if (result.isSelected()) {
        updatedList.add(column);
      }
    }

    return updatedList;
  }

  protected JMenuItem createAllLogTableColumnsMenuItem() {
    JMenuItem result = new JMenuItem("Show all Columns");
    result.setMnemonic('s');
    result.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        selectAllLogTableColumns(true);
        // update list of columns and reset the view
        List selectedColumns = updateView();
        logBrokerMonitorR.get_table().setView(selectedColumns);
      }
    });
    return result;
  }

  protected JMenuItem createNoLogTableColumnsMenuItem() {
    JMenuItem result = new JMenuItem("Hide all Columns");
    result.setMnemonic('h');
    result.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        selectAllLogTableColumns(false);
        // update list of columns and reset the view
        List selectedColumns = updateView();
        logBrokerMonitorR.get_table().setView(selectedColumns);
      }
    });
    return result;
  }

  protected void selectAllLogTableColumns(boolean selected) {
    Iterator columns = getLogTableColumns();
    while (columns.hasNext()) {
      getLogTableColumnMenuItem((LogTableColumn) columns.next()).setSelected(selected);
    }
  }

  protected JMenu createFileMenu() {
    JMenu fileMenu = new JMenu("File");
    fileMenu.setMnemonic('f');
    JMenuItem exitMI;
    fileMenu.add(createOpenMI());
    fileMenu.add(createOpenURLMI());
    fileMenu.addSeparator();
    fileMenu.add(createCloseMI());
    createMRUFileListMI(fileMenu);
    fileMenu.addSeparator();
    fileMenu.add(createExitMI());
    return fileMenu;
  }

  /**
   * Menu item added to allow log files to be opened with
   * the LF5 GUI.
   */
  protected JMenuItem createOpenMI() {
    JMenuItem result = new JMenuItem("Open...");
    result.setMnemonic('o');
    result.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        requestOpen();
      }
    });
    return result;
  }

  /**
   * Menu item added to allow log files loaded from a URL
   * to be opened by the LF5 GUI.
   */
  protected JMenuItem createOpenURLMI() {
    JMenuItem result = new JMenuItem("Open URL...");
    result.setMnemonic('u');
    result.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        requestOpenURL();
      }
    });
    return result;
  }

  protected JMenuItem createCloseMI() {
    JMenuItem result = new JMenuItem("Close");
    result.setMnemonic('c');
    result.setAccelerator(KeyStroke.getKeyStroke("control Q"));
    result.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        requestClose();
      }
    });
    return result;
  }

  /**
   * Creates a Most Recently Used file list to be
   * displayed in the File menu
   */
  protected void createMRUFileListMI(JMenu menu) {

    String[] files = _mruFileManager.getMRUFileList();

    if (files != null) {
      menu.addSeparator();
      for (int i = 0; i < files.length; i++) {
        JMenuItem result = new JMenuItem((i + 1) + " " + files[i]);
        result.setMnemonic(i + 1);
        result.addActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            requestOpenMRU(e);
          }
        });
        menu.add(result);
      }
    }
  }

  protected JMenuItem createExitMI() {
    JMenuItem result = new JMenuItem("Exit");
    result.setMnemonic('x');
    result.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        requestExit();
      }
    });
    return result;
  }

  protected JMenu createConfigureMenu() {
    JMenu configureMenu = new JMenu("Configure");
    configureMenu.setMnemonic('c');
    configureMenu.add(createConfigureSave());
    configureMenu.add(createConfigureReset());
    configureMenu.add(createConfigureMaxRecords());

    return configureMenu;
  }

  protected JMenuItem createConfigureSave() {
    JMenuItem result = new JMenuItem("Save");
    result.setMnemonic('s');
    result.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        saveConfiguration();
      }
    });

    return result;
  }

  protected JMenuItem createConfigureReset() {
    JMenuItem result = new JMenuItem("Reset");
    result.setMnemonic('r');
    result.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        resetConfiguration();
      }
    });

    return result;
  }

  protected JMenuItem createConfigureMaxRecords() {
    JMenuItem result = new JMenuItem("Set Max Number of Records");
    result.setMnemonic('m');
    result.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        setMaxRecordConfiguration();
      }
    });

    return result;
  }


  protected void saveConfiguration() {
    _configurationManager.save();
  }

  protected void resetConfiguration() {
    _configurationManager.reset();
  }

  protected void setMaxRecordConfiguration() {
    LogFactor5InputDialog inputDialog = new LogFactor5InputDialog(
        getBaseFrame(), "Set Max Number of Records", "", 10);

    String temp = inputDialog.getText();

    if (temp != null) {
      try {
        setMaxNumberOfLogRecords(Integer.parseInt(temp));
      } catch (NumberFormatException e) {
        LogFactor5ErrorDialog error = new LogFactor5ErrorDialog(
            getBaseFrame(),
            "'" + temp + "' is an invalid parameter.\nPlease try again.");
        setMaxRecordConfiguration();
      }
    }
  }


  protected JMenu createHelpMenu() {
    JMenu helpMenu = new JMenu("Help");
    helpMenu.setMnemonic('h');
    helpMenu.add(createHelpProperties());
    return helpMenu;
  }

  protected JMenuItem createHelpProperties() {
    final String title = "LogFactor5 Properties";
    final JMenuItem result = new JMenuItem(title);
    result.setMnemonic('l');
    result.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        showPropertiesDialog(title);
      }
    });
    return result;
  }

  protected void showPropertiesDialog(String title) {
    JOptionPane.showMessageDialog(
            logBrokerMonitorR.get_logMonitorFrame(),
        _displayedLogBrokerProperties.toArray(),
        title,
        JOptionPane.PLAIN_MESSAGE
    );
  }

  protected JMenu createEditMenu() {
    JMenu editMenu = new JMenu("Edit");
    editMenu.setMnemonic('e');
    editMenu.add(createEditFindMI());
    editMenu.add(createEditFindNextMI());
    editMenu.addSeparator();
    editMenu.add(createEditSortNDCMI());
    editMenu.add(createEditRestoreAllNDCMI());
    return editMenu;
  }

  protected JMenuItem createEditFindNextMI() {
    JMenuItem editFindNextMI = new JMenuItem("Find Next");
    editFindNextMI.setMnemonic('n');
    editFindNextMI.setAccelerator(KeyStroke.getKeyStroke("F3"));
    editFindNextMI.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        findSearchText();
      }
    });
    return editFindNextMI;
  }

  protected JMenuItem createEditFindMI() {
    JMenuItem editFindMI = new JMenuItem("Find");
    editFindMI.setMnemonic('f');
    editFindMI.setAccelerator(KeyStroke.getKeyStroke("control F"));

    editFindMI.addActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            String inputValue =
                JOptionPane.showInputDialog(
                        logBrokerMonitorR.get_logMonitorFrame(),
                    "Find text: ",
                    "Search Record Messages",
                    JOptionPane.QUESTION_MESSAGE
                );
            setSearchText(inputValue);
            findSearchText();
          }
        }

    );
    return editFindMI;
  }

  // Added version 1.2 - Allows users to Sort Log Records by an
  // NDC text filter. A new LogRecordFilter was created to
  // sort the records.
  protected JMenuItem createEditSortNDCMI() {
    JMenuItem editSortNDCMI = new JMenuItem("Sort by NDC");
    editSortNDCMI.setMnemonic('s');
    editSortNDCMI.addActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            String inputValue =
                JOptionPane.showInputDialog(
                        logBrokerMonitorR.get_logMonitorFrame(),
                    "Sort by this NDC: ",
                    "Sort Log Records by NDC",
                    JOptionPane.QUESTION_MESSAGE
                );
            setNDCTextFilter(inputValue);
            sortByNDC();
            logBrokerMonitorR.get_table().getFilteredLogTableModel().refresh();
            logBrokerMonitorR.updateStatusLabel();
          }
        }

    );
    return editSortNDCMI;
  }

  // Added in version 1.2 - Resets the LogRecordFilter back to default
  // filter.
  protected JMenuItem createEditRestoreAllNDCMI() {
    JMenuItem editRestoreAllNDCMI = new JMenuItem("Restore all NDCs");
    editRestoreAllNDCMI.setMnemonic('r');
    editRestoreAllNDCMI.addActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            logBrokerMonitorR.get_table().getFilteredLogTableModel().setLogRecordFilter(createLogRecordFilter());
            // reset the text filter
            setNDCTextFilter("");
            logBrokerMonitorR.get_table().getFilteredLogTableModel().refresh();
            logBrokerMonitorR.updateStatusLabel();
          }
        }
    );
    return editRestoreAllNDCMI;
  }

  protected JToolBar createToolBar() {
    JToolBar tb = new JToolBar();
    tb.putClientProperty("JToolBar.isRollover", Boolean.TRUE);
    JComboBox fontCombo = new JComboBox();
    JComboBox fontSizeCombo = new JComboBox();
    logBrokerMonitorR.set_fontSizeCombo(fontSizeCombo);

    ClassLoader cl = this.getClass().getClassLoader();
    if(cl == null) {
        cl = ClassLoader.getSystemClassLoader();
    }
    URL newIconURL = cl.getResource("org/apache/log4j/lf5/viewer/" +
        "images/channelexplorer_new.gif");

    ImageIcon newIcon = null;

    if (newIconURL != null) {
      newIcon = new ImageIcon(newIconURL);
    }

    JButton newButton = new JButton("Clear Log Table");

    if (newIcon != null) {
      newButton.setIcon(newIcon);
    }

    newButton.setToolTipText("Clear Log Table.");
    //newButton.setBorder(BorderFactory.createEtchedBorder());

    newButton.addActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            logBrokerMonitorR.get_table().clearLogRecords();
            logBrokerMonitorR.get_categoryExplorerTree().getExplorerModel().resetAllNodeCounts();
            logBrokerMonitorR.updateStatusLabel();
            clearDetailTextArea();
            LogRecord.resetSequenceNumber();
          }
        }
    );

    Toolkit tk = Toolkit.getDefaultToolkit();
    // This will actually grab all the fonts

    String[] fonts;

    if (_loadSystemFonts) {
      fonts = GraphicsEnvironment.
          getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
    } else {
      fonts = tk.getFontList();
    }

    for (int j = 0; j < fonts.length; j++) {
      fontCombo.addItem(fonts[j]);
    }

    fontCombo.setSelectedItem(_fontName);

    fontCombo.addActionListener(

        new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            JComboBox box = (JComboBox) e.getSource();
            String font = (String) box.getSelectedItem();
            logBrokerMonitorR.get_table().setFont(new Font(font, Font.PLAIN, logBrokerMonitorR.get_fontSize()));
            _fontName = font;
          }
        }
    );

    fontSizeCombo.addItem("8");
    fontSizeCombo.addItem("9");
    fontSizeCombo.addItem("10");
    fontSizeCombo.addItem("12");
    fontSizeCombo.addItem("14");
    fontSizeCombo.addItem("16");
    fontSizeCombo.addItem("18");
    fontSizeCombo.addItem("24");

    fontSizeCombo.setSelectedItem(String.valueOf(logBrokerMonitorR.get_fontSize()));
    fontSizeCombo.addActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            JComboBox box = (JComboBox) e.getSource();
            String size = (String) box.getSelectedItem();
            int s = Integer.valueOf(size).intValue();

            setFontSizeSilently(s);
            refreshDetailTextArea();
            logBrokerMonitorR.set_fontSize(s);
          }
        }
    );

    tb.add(new JLabel(" Font: "));
    tb.add(fontCombo);
    tb.add(fontSizeCombo);
    tb.addSeparator();
    tb.addSeparator();
    tb.add(newButton);

    newButton.setAlignmentY(0.5f);
    newButton.setAlignmentX(0.5f);

    fontCombo.setMaximumSize(fontCombo.getPreferredSize());
    fontSizeCombo.setMaximumSize(
        fontSizeCombo.getPreferredSize());

    return (tb);
  }

//    protected void setView(String viewString, LogTable table) {
//        if (STANDARD_VIEW.equals(viewString)) {
//            table.setStandardView();
//        } else if (COMPACT_VIEW.equals(viewString)) {
//            table.setCompactView();
//        } else if (DETAILED_VIEW.equals(viewString)) {
//            table.setDetailedView();
//        } else {
//            String message = viewString + "does not match a supported view.";
//            throw new IllegalArgumentException(message);
//        }
//        _currentView = viewString;
//    }

  protected void setView(String viewString, LogTable table) {
    if (LogBrokerMonitorR.DETAILED_VIEW.equals(viewString)) {
      table.setDetailedView();
    } else {
      String message = viewString + "does not match a supported view.";
      throw new IllegalArgumentException(message);
    }
    _currentView = viewString;
  }

  protected JComboBox createLogLevelCombo() {
    JComboBox result = new JComboBox();
    Iterator levels = getLogLevels();
    while (levels.hasNext()) {
      result.addItem(levels.next());
    }
    result.setSelectedItem(logBrokerMonitorR.get_leastSevereDisplayedLogLevel());

    result.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        JComboBox box = (JComboBox) e.getSource();
        LogLevel level = (LogLevel) box.getSelectedItem();
        setLeastSevereDisplayedLogLevel(level);
      }
    });
    result.setMaximumSize(result.getPreferredSize());
    return result;
  }

  protected void setLeastSevereDisplayedLogLevel(LogLevel level) {
    if (level == null || logBrokerMonitorR.get_leastSevereDisplayedLogLevel() == level) {
      return; // nothing to do
    }
    logBrokerMonitorR.set_leastSevereDisplayedLogLevel(level);
    logBrokerMonitorR.get_table().getFilteredLogTableModel().refresh();
    logBrokerMonitorR.updateStatusLabel();
  }

  /**
   * Ensures that the Table's ScrollPane Viewport will "track" with updates
   * to the Table.  When the vertical scroll bar is at its bottom anchor
   * and tracking is enabled then viewport will stay at the bottom most
   * point of the component.  The purpose of this feature is to allow
   * a developer to watch the table as messages arrive and not have to
   * scroll after each new message arrives.  When the vertical scroll bar
   * is at any other location, then no tracking will happen.
   * @deprecated tracking is now done automatically.
   */
  protected void trackTableScrollPane() {
    // do nothing
    logBrokerMonitorR.trackTableScrollPane();
  }

  protected void centerFrame(JFrame frame) {

    logBrokerMonitorR.centerFrame(frame);
  }

  /**
   * Uses a JFileChooser to select a file to opened with the
   * LF5 GUI.
   */
  protected void requestOpen() {
    JFileChooser chooser;

    if (_fileLocation == null) {
      chooser = new JFileChooser();
    } else {
      chooser = new JFileChooser(_fileLocation);
    }

    int returnVal = chooser.showOpenDialog(logBrokerMonitorR.get_logMonitorFrame());
    if (returnVal == JFileChooser.APPROVE_OPTION) {
      File f = chooser.getSelectedFile();
      if (loadLogFile(f)) {
        _fileLocation = chooser.getSelectedFile();
        _mruFileManager.set(f);
        updateMRUList();
      }
    }
  }

  /**
   * Uses a Dialog box to accept a URL to a file to be opened
   * with the LF5 GUI.
   */
  protected void requestOpenURL() {
    LogFactor5InputDialog inputDialog = new LogFactor5InputDialog(
        getBaseFrame(), "Open URL", "URL:");
    String temp = inputDialog.getText();

    if (temp != null) {
      if (temp.indexOf("://") == -1) {
        temp = "http://" + temp;
      }

      try {
        URL url = new URL(temp);
        if (loadLogFile(url)) {
          _mruFileManager.set(url);
          updateMRUList();
        }
      } catch (MalformedURLException e) {
        LogFactor5ErrorDialog error = new LogFactor5ErrorDialog(
            getBaseFrame(), "Error reading URL.");
      }
    }
  }

  /**
   * Removes old file list and creates a new file list
   * with the updated MRU list.
   */
  protected void updateMRUList() {
    JMenu menu = logBrokerMonitorR.get_logMonitorFrame().getJMenuBar().getMenu(0);
    menu.removeAll();
    menu.add(createOpenMI());
    menu.add(createOpenURLMI());
    menu.addSeparator();
    menu.add(createCloseMI());
    createMRUFileListMI(menu);
    menu.addSeparator();
    menu.add(createExitMI());
  }

  protected void requestClose() {
    setCallSystemExitOnClose(false);
    closeAfterConfirm();
  }

  /**
   * Opens a file in the MRU list.
   */
  protected void requestOpenMRU(ActionEvent e) {
    String file = e.getActionCommand();
    StringTokenizer st = new StringTokenizer(file);
    String num = st.nextToken().trim();
    file = st.nextToken("\n");

    try {
      int index = Integer.parseInt(num) - 1;

      InputStream in = _mruFileManager.getInputStream(index);
      LogFileParser lfp = new LogFileParser(in);
      lfp.parse(this);

      _mruFileManager.moveToTop(index);
      updateMRUList();

    } catch (Exception me) {
      LogFactor5ErrorDialog error = new LogFactor5ErrorDialog(
          getBaseFrame(), "Unable to load file " + file);
    }

  }

  protected void requestExit() {
    _mruFileManager.save();
    setCallSystemExitOnClose(true);
    closeAfterConfirm();
  }

  protected void closeAfterConfirm() {
    StringBuffer message = new StringBuffer();

    if (_callSystemExitOnClose == false) {
      message.append("Are you sure you want to close the logging ");
      message.append("console?\n");
      message.append("(Note: This will not shut down the Virtual Machine,\n");
      message.append("or the Swing event thread.)");
    } else {
      message.append("Are you sure you want to exit?\n");
      message.append("This will shut down the Virtual Machine.\n");
    }

    String title =
        "Are you sure you want to dispose of the Logging Console?";

    if (_callSystemExitOnClose == true) {
      title = "Are you sure you want to exit?";
    }
    int value = JOptionPane.showConfirmDialog(
            logBrokerMonitorR.get_logMonitorFrame(),
        message.toString(),
        title,
        JOptionPane.OK_CANCEL_OPTION,
        JOptionPane.QUESTION_MESSAGE,
        null
    );

    if (value == JOptionPane.OK_OPTION) {
      dispose();
    }
  }

  protected Iterator getLogLevels() {
    return _levels.iterator();
  }

  protected Iterator getLogTableColumns() {
    return _columns.iterator();
  }

  /**
   * Loads and parses a log file.
   */
  protected boolean loadLogFile(File file) {
    boolean ok = false;
    try {
      LogFileParser lfp = new LogFileParser(file);
      lfp.parse(this);
      ok = true;
    } catch (IOException e) {
      LogFactor5ErrorDialog error = new LogFactor5ErrorDialog(
          getBaseFrame(), "Error reading " + file.getName());
    }

    return ok;
  }

  /**
   * Loads a parses a log file running on a server.
   */
  protected boolean loadLogFile(URL url) {
    boolean ok = false;
    try {
      LogFileParser lfp = new LogFileParser(url.openStream());
      lfp.parse(this);
      ok = true;
    } catch (IOException e) {
      LogFactor5ErrorDialog error = new LogFactor5ErrorDialog(
          getBaseFrame(), "Error reading URL:" + url.getFile());
    }
    return ok;
  }

  public static class LogBrokerMonitorR {
    public static final String DETAILED_VIEW = "Detailed";//    public static final String STANDARD_VIEW = "Standard";
    //    public static final String COMPACT_VIEW = "Compact";
    //--------------------------------------------------------------------------
    //   Protected Variables:
    //--------------------------------------------------------------------------
    public JFrame _logMonitorFrame;

    public JFrame get_logMonitorFrame() {
      return _logMonitorFrame;
    }

    public void set_logMonitorFrame(JFrame _logMonitorFrame) {
      this._logMonitorFrame = _logMonitorFrame;
    }

    public int _logMonitorFrameWidth = 550;

    public int get_logMonitorFrameWidth() {
      return _logMonitorFrameWidth;
    }

    public void set_logMonitorFrameWidth(int _logMonitorFrameWidth) {
      this._logMonitorFrameWidth = _logMonitorFrameWidth;
    }

    public int _logMonitorFrameHeight = 500;

    public int get_logMonitorFrameHeight() {
      return _logMonitorFrameHeight;
    }

    public void set_logMonitorFrameHeight(int _logMonitorFrameHeight) {
      this._logMonitorFrameHeight = _logMonitorFrameHeight;
    }

    public LogTable _table;

    public LogTable get_table() {
      return _table;
    }

    public void set_table(LogTable _table) {
      this._table = _table;
    }

    public CategoryExplorerTree _categoryExplorerTree;

    public CategoryExplorerTree get_categoryExplorerTree() {
      return _categoryExplorerTree;
    }

    public void set_categoryExplorerTree(CategoryExplorerTree _categoryExplorerTree) {
      this._categoryExplorerTree = _categoryExplorerTree;
    }

    public String _searchText;

    public String get_searchText() {
      return _searchText;
    }

    public void set_searchText(String _searchText) {
      this._searchText = _searchText;
    }

    public String _NDCTextFilter = "";

    public String get_NDCTextFilter() {
      return _NDCTextFilter;
    }

    public void set_NDCTextFilter(String _NDCTextFilter) {
      this._NDCTextFilter = _NDCTextFilter;
    }

    public LogLevel _leastSevereDisplayedLogLevel = LogLevel.DEBUG;

    public LogLevel get_leastSevereDisplayedLogLevel() {
      return _leastSevereDisplayedLogLevel;
    }

    public void set_leastSevereDisplayedLogLevel(LogLevel _leastSevereDisplayedLogLevel) {
      this._leastSevereDisplayedLogLevel = _leastSevereDisplayedLogLevel;
    }

    public JScrollPane _logTableScrollPane;

    public JScrollPane get_logTableScrollPane() {
      return _logTableScrollPane;
    }

    public void set_logTableScrollPane(JScrollPane _logTableScrollPane) {
      this._logTableScrollPane = _logTableScrollPane;
    }

    public JLabel _statusLabel;
    public Object _lock = new Object();
    public JComboBox _fontSizeCombo;

    public JComboBox get_fontSizeCombo() {
      return _fontSizeCombo;
    }

    public void set_fontSizeCombo(JComboBox _fontSizeCombo) {
      this._fontSizeCombo = _fontSizeCombo;
    }

    public int _fontSize = 10;

    public int get_fontSize() {
      return _fontSize;
    }

    public void set_fontSize(int _fontSize) {
      this._fontSize = _fontSize;
    }

    public LogBrokerMonitorR() {
    }

    /**
     * Changes the font selection in the combo box and returns the
     * size actually selected.
     *
     * @return -1 if unable to select an appropriate font
     */
    public int changeFontSizeCombo(JComboBox box, int requestedSize) {
      int len = box.getItemCount();
      int currentValue;
      Object currentObject;
      Object selectedObject = box.getItemAt(0);
      int selectedValue = Integer.parseInt(String.valueOf(selectedObject));
      for (int i = 0; i < len; i++) {
        currentObject = box.getItemAt(i);
        currentValue = Integer.parseInt(String.valueOf(currentObject));
        if (selectedValue < currentValue && currentValue <= requestedSize) {
          selectedValue = currentValue;
          selectedObject = currentObject;
        }
      }
      box.setSelectedItem(selectedObject);
      return selectedValue;
    }

    public void setFontSize(Component component, int fontSize) {
      Font oldFont = component.getFont();
      Font newFont =
              new Font(oldFont.getFontName(), oldFont.getStyle(), fontSize);
      component.setFont(newFont);
    }

    public void updateFrameSize() {
      _logMonitorFrame.setSize(_logMonitorFrameWidth, _logMonitorFrameHeight);
      centerFrame(_logMonitorFrame);
    }

    public void updateStatusLabel() {
      _statusLabel.setText(getRecordsDisplayedMessage());
    }

    public String getRecordsDisplayedMessage() {
      FilteredLogTableModel model = _table.getFilteredLogTableModel();
      return getStatusText(model.getRowCount(), model.getTotalRowCount());
    }

    public String getStatusText(int displayedRows, int totalRows) {
      StringBuffer result = new StringBuffer();
      result.append("Displaying: ");
      result.append(displayedRows);
      result.append(" records out of a total of: ");
      result.append(totalRows);
      result.append(" records.");
      return result.toString();
    }

    public JPanel createStatusArea() {
      JPanel statusArea = new JPanel();
      JLabel status =
              new JLabel("No log records to display.");
      _statusLabel = status;
      status.setHorizontalAlignment(JLabel.LEFT);

      statusArea.setBorder(BorderFactory.createEtchedBorder());
      statusArea.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
      statusArea.add(status);

      return (statusArea);
    }

    public JTextArea createDetailTextArea() {
      JTextArea detailTA = new JTextArea();
      detailTA.setFont(new Font("Monospaced", Font.PLAIN, 14));
      detailTA.setTabSize(3);
      detailTA.setLineWrap(true);
      detailTA.setWrapStyleWord(false);
      return (detailTA);
    }

    /**
     * Ensures that the Table's ScrollPane Viewport will "track" with updates
     * to the Table.  When the vertical scroll bar is at its bottom anchor
     * and tracking is enabled then viewport will stay at the bottom most
     * point of the component.  The purpose of this feature is to allow
     * a developer to watch the table as messages arrive and not have to
     * scroll after each new message arrives.  When the vertical scroll bar
     * is at any other location, then no tracking will happen.
     *
     * @deprecated tracking is now done automatically.
     */
    public void trackTableScrollPane() {
      // do nothing
    }

    public void centerFrame(JFrame frame) {
      Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
      Dimension comp = frame.getSize();

      frame.setLocation(((screen.width - comp.width) / 2),
              ((screen.height - comp.height) / 2));

    }

    class LogBrokerMonitorWindowAdaptor extends WindowAdapter {
      protected org.apache.log4j.lf5.viewer.LogBrokerMonitor _monitor;

      public LogBrokerMonitorWindowAdaptor(org.apache.log4j.lf5.viewer.LogBrokerMonitor monitor) {
        _monitor = monitor;
      }

      public void windowClosing(WindowEvent ev) {
        _monitor.requestClose();
      }
    }
  }

  //--------------------------------------------------------------------------
  //   Private Methods:
  //--------------------------------------------------------------------------

  //--------------------------------------------------------------------------
  //   Nested Top-Level Classes or Interfaces:
  //--------------------------------------------------------------------------

}


