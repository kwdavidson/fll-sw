/*
 * Copyright (c) 2000-2002 INSciTE.  All rights reserved
 * INSciTE is on the web at: http://www.hightechkids.org
 * This code is released under GPL; see LICENSE.txt for details.
 */
package fll.gui;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.prefs.Preferences;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextPane;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.StyledDocument;

import net.mtu.eggplant.util.BasicFileFilter;
import net.mtu.eggplant.util.gui.SortableTable;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import fll.model.SubjectiveTableModel;
import fll.xml.ChallengeParser;
import fll.xml.XMLUtils;
import fll.xml.XMLWriter;

/**
 * Application to enter subjective scores with
 * 
 * @version $Revision$
 */
public final class SubjectiveFrame extends JFrame {

  public static void main(final String[] args) {
    try {
      final File initialDirectory = getInitialDirectory();
      final JFileChooser fileChooser = new JFileChooser(initialDirectory);
      fileChooser.setDialogTitle("Please choose the subjective data file");
      // NOTE: Should get JonsInfra fixed for this
      fileChooser.setFileFilter(new BasicFileFilter("Zip files", "zip") {
        public boolean accept(final File f) {
          if(f.isDirectory()) {
            return true;
          } else {
            return super.accept(f);
          }
        }
      });
      final int state = fileChooser.showOpenDialog(null);
      if(JFileChooser.APPROVE_OPTION == state) {
        final File file = fileChooser.getSelectedFile();
        final SubjectiveFrame frame = new SubjectiveFrame(file);
        setInitialDirectory(file);
        frame.pack();
        frame.setVisible(true);
      } else {
        System.exit(0);
      }
    } catch(final IOException ioe) {
      JOptionPane.showMessageDialog(null, "Error reading data file: " + ioe.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
      System.exit(1);
    }
  }

  /**
   * Create a window to edit subjective scores.
   * 
   * @param file
   *          where to read the data in from and where to save data to
   */
  public SubjectiveFrame(final File file) throws IOException {
    super("Subjective Score Entry");
    _file = file;

    getContentPane().setLayout(new BorderLayout());

    final ZipFile zipfile = new ZipFile(file);
    final InputStream challengeStream = zipfile.getInputStream(zipfile.getEntry("challenge.xml"));
    _challengeDocument = ChallengeParser.parse(new InputStreamReader(challengeStream));
    challengeStream.close();

    final InputStream scoreStream = zipfile.getInputStream(zipfile.getEntry("score.xml"));
    _scoreDocument = XMLUtils.parseXMLDocument(scoreStream);
    scoreStream.close();

    zipfile.close();

    final JPanel topPanel = new JPanel();
    getContentPane().add(topPanel, BorderLayout.NORTH);

    final JButton quitButton = new JButton("Quit");
    topPanel.add(quitButton);
    quitButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent ae) {
        quit();
      }
    });

    final JButton saveButton = new JButton("Save");
    topPanel.add(saveButton);
    saveButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent ae) {
        try {
          save();
        } catch(final IOException ioe) {
          JOptionPane.showMessageDialog(null, "Error writing to data file: " + ioe.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }

      }
    });

    final JButton summaryButton = new JButton("Summary");
    topPanel.add(summaryButton);
    summaryButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent ae) {
        final SummaryDialog dialog = new SummaryDialog(SubjectiveFrame.this);

        dialog.pack();
        dialog.setVisible(true);
      }
    });

    final JTabbedPane tabbedPane = new JTabbedPane();
    getContentPane().add(tabbedPane, BorderLayout.CENTER);

    final NodeList subjectiveCategories = _challengeDocument.getDocumentElement().getElementsByTagName("subjectiveCategory");
    for(int i = 0; i < subjectiveCategories.getLength(); i++) {
      final Element subjectiveElement = (Element)subjectiveCategories.item(i);
      final SubjectiveTableModel tableModel = new SubjectiveTableModel(_scoreDocument, subjectiveElement);
      final JTable table = new SortableTable(tableModel);
      final String title = subjectiveElement.getAttribute("title");
      _tables.put(title, table);
      final JScrollPane tableScroller = new JScrollPane(table);
      tableScroller.setPreferredSize(new Dimension(640, 480));
      tabbedPane.addTab(title, tableScroller);

      final NodeList goals = subjectiveElement.getElementsByTagName("goal");
      for(int g = 0; g < goals.getLength(); g++) {
        final Element goalDescription = (Element)goals.item(g);
        final NodeList posValuesList = goalDescription.getElementsByTagName("value");
        if(posValuesList.getLength() > 0) {
          // enumerated
          final Vector<String> posValues = new Vector<String>();
          posValues.add("");
          for(int v = 0; v < posValuesList.getLength(); v++) {
            final Element posValue = (Element)posValuesList.item(v);
            posValues.add(posValue.getAttribute("title"));
          }

          final TableColumn column = table.getColumnModel().getColumn(g + 4);
          column.setCellEditor(new DefaultCellEditor(new JComboBox(posValues)));
        }
      }
    }

    addWindowListener(new WindowAdapter() {
      public void windowClosing(final WindowEvent e) {
        quit();
      }
    });
    setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
  }

  /**
   * Prompt the user with yes/no/cancel. Yes exits and saves, no exits without
   * saving and cancel doesn't quit.
   */
  private void quit() {
    if(validateData()) {

      final int state = JOptionPane.showConfirmDialog(SubjectiveFrame.this, "Save data?  Data will be saved in same file as it was read from.",
          "Exit", JOptionPane.YES_NO_CANCEL_OPTION);
      if(JOptionPane.YES_OPTION == state) {
        try {
          save();
          setVisible(false);
          dispose();
        } catch(final IOException ioe) {
          JOptionPane.showMessageDialog(null, "Error writing to data file: " + ioe.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }

        System.exit(0);
      } else if(JOptionPane.NO_OPTION == state) {
        setVisible(false);
        dispose();
        System.exit(0);
      }
    }
  }

  /**
   * Make sure the data in the table is valid. This checks to make sure that for
   * all rows, all columns that contain numeric data are actually set, or none
   * of these columns are set in a row. This avoids the case of partial data.
   * This method is fail fast in that it will display a dialog box on the first
   * error it finds.
   * 
   * @return true if everything is ok
   */
  private boolean validateData() {
    stopCellEditors();

    final List<String> warnings = new LinkedList<String>();
    final NodeList subjectiveCategories = _challengeDocument.getDocumentElement().getElementsByTagName("subjectiveCategory");
    for(int i = 0; i < subjectiveCategories.getLength(); i++) {
      final Element subjectiveElement = (Element)subjectiveCategories.item(i);
      final String category = subjectiveElement.getAttribute("name");
      final String categoryTitle = subjectiveElement.getAttribute("title");

      final NodeList goals = subjectiveElement.getElementsByTagName("goal");
      final Element categoryElement = (Element)_scoreDocument.getDocumentElement().getElementsByTagName(category).item(0);
      final NodeList scores = categoryElement.getElementsByTagName("score");
      for(int scoreIdx = 0; scoreIdx < scores.getLength(); scoreIdx++) {
        int numValues = 0;
        final Element scoreElement = (Element)scores.item(scoreIdx);
        for(int goalIdx = 0; goalIdx < goals.getLength(); goalIdx++) {
          final Element goalElement = (Element)goals.item(goalIdx);
          final String goalName = goalElement.getAttribute("name");
          final String value = scoreElement.getAttribute(goalName);
          if(null != value && !"".equals(value)) {
            numValues++;
          }
        }
        if(numValues != goals.getLength() && numValues != 0) {
          warnings.add(categoryTitle + ": " + scoreElement.getAttribute("teamNumber") + " has too few scores (needs all or none): " + numValues);
        }

      }
    }

    if(!warnings.isEmpty()) {
      // join the warnings with carriage returns and display them
      final StyledDocument doc = new DefaultStyledDocument();
      for(String warning : warnings) {
        try {
          doc.insertString(doc.getLength(), warning + "\n", null);
        } catch(final BadLocationException ble) {
          throw new RuntimeException(ble);
        }
      }
      final JDialog dialog = new JDialog(this, "Warnings");
      final Container cpane = dialog.getContentPane();
      cpane.setLayout(new BorderLayout());
      final JButton okButton = new JButton("Ok");
      cpane.add(okButton, BorderLayout.SOUTH);
      okButton.addActionListener(new ActionListener() {
        public void actionPerformed(final ActionEvent ae) {
          dialog.setVisible(false);
          dialog.dispose();
        }
      });
      cpane.add(new JTextPane(doc), BorderLayout.CENTER);
      dialog.pack();
      dialog.setVisible(true);
      return false;
    } else {
      return true;
    }
  }

  /**
   * Stop the cell editors to ensure data is flushed
   */
  private void stopCellEditors() {
    final Iterator<JTable> iter = _tables.values().iterator();
    while(iter.hasNext()) {
      final JTable table = iter.next();
      final int editingColumn = table.getEditingColumn();
      final int editingRow = table.getEditingRow();
      if(editingColumn > -1) {
        final TableCellEditor cellEditor = table.getCellEditor(editingRow, editingColumn);
        if(null != cellEditor) {
          cellEditor.stopCellEditing();
        }
      }
    }
  }

  /**
   * Save out to the same file that things were read in.
   * 
   * @throws IOException
   *           if an error occurs writing to the file
   */
  public void save() throws IOException {
    if(validateData()) {

      final XMLWriter xmlwriter = new XMLWriter();

      final ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(_file));
      xmlwriter.setOutput(zipOut, "UTF8");

      zipOut.putNextEntry(new ZipEntry("challenge.xml"));
      xmlwriter.write(_challengeDocument);
      zipOut.closeEntry();
      zipOut.putNextEntry(new ZipEntry("score.xml"));
      xmlwriter.write(_scoreDocument);
      zipOut.closeEntry();

      zipOut.close();
    }
  }

  /**
   * Set the initial directory preference. This supports opening new file
   * dialogs to a (hopefully) better default in the user's next session.
   * 
   * @param dir
   *          the File for the directory in which file dialogs should open
   */
  private static void setInitialDirectory(final File dir) {
    // Store only directories
    final File directory;
    if(dir.isDirectory()) {
      directory = dir;
    } else {
      directory = dir.getParentFile();
    }

    final Preferences preferences = Preferences.userNodeForPackage(SubjectiveFrame.class);
    final String previousPath = preferences.get(INITIAL_DIRECTORY_PREFERENCE_KEY, null);

    if(!directory.toString().equals(previousPath)) {
      preferences.put(INITIAL_DIRECTORY_PREFERENCE_KEY, directory.toString());
    }
  }

  /**
   * Get the initial directory to which file dialogs should open. This supports
   * opening to a better directory across sessions.
   * 
   * @return the File for the initial directory
   */
  private static File getInitialDirectory() {
    final Preferences preferences = Preferences.userNodeForPackage(SubjectiveFrame.class);
    final String path = preferences.get(INITIAL_DIRECTORY_PREFERENCE_KEY, null);

    File dir = null;
    if(null != path) {
      dir = new File(path);
    }
    return dir;
  }

  /**
   * Preferences key for file dialog initial directory
   */
  private static final String INITIAL_DIRECTORY_PREFERENCE_KEY = "InitialDirectory";

  private Map<String, JTable> _tables = new HashMap<String, JTable>();

  /**
   * Get the table model for a given subjective title. Mostly for testing.
   * 
   * @return the table model or null if the specified title is not present
   */
  public TableModel getTableModelForTitle(final String title) {
    final JTable table = _tables.get(title);
    if(null == table) {
      return null;
    } else {
      return table.getModel();
    }
  }

  private final File _file;

  private final Document _challengeDocument;
  /*package*/ final Document getChallengeDocument() {
    return _challengeDocument;
  }
  private final Document _scoreDocument;
  /*package*/ final Document getScoreDocument() {
    return _scoreDocument;
  }

}
