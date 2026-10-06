package com.netapps.filetransfer.ui;

import com.netapps.filetransfer.client.FileClient;
import com.netapps.filetransfer.client.TransferListener;
import com.netapps.filetransfer.client.TransferTask;
import com.netapps.filetransfer.common.FileInfo;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Main application window for the File Transfer client application.
 * Manages remote file explorer table, actions (Upload, Download, Delete, Refresh),
 * and dispatches all network operations on background worker threads.
 */
public class MainWindow extends JFrame {

    private final FileClient client = new FileClient();

    private final LoginPanel loginPanel = new LoginPanel();
    private final FileTableModel tableModel = new FileTableModel();
    private final JTable fileTable = new JTable(tableModel);
    private final TransferPanel transferPanel = new TransferPanel();

    private final JButton uploadButton = new JButton("Upload File");
    private final JButton downloadButton = new JButton("Download");
    private final JButton deleteButton = new JButton("Delete");
    private final JButton refreshButton = new JButton("Refresh");
    private final JLabel statusBar = new JLabel(" Ready - Not connected");

    /**
     * Constructs the main application window and configures Swing UI components.
     */
    public MainWindow() {
        super("Secure Resumable File Transfer Client");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(920, 680);
        setMinimumSize(new Dimension(750, 500));
        setLocationRelativeTo(null);

        initUi();
        setupListeners();
    }

    /**
     * Lays out the window UI components.
     */
    private void initUi() {
        setLayout(new BorderLayout());

        // Top Login panel
        add(loginPanel, BorderLayout.NORTH);

        // Center: File Table and Action Toolbar
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        fileTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fileTable.setRowHeight(22);
        fileTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        JScrollPane tableScroll = new JScrollPane(fileTable);
        tableScroll.setBorder(BorderFactory.createTitledBorder("Remote User Storage"));
        centerPanel.add(tableScroll, BorderLayout.CENTER);

        // Action Toolbar
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        toolbar.add(uploadButton);
        toolbar.add(downloadButton);
        toolbar.add(deleteButton);
        toolbar.add(refreshButton);

        setFileActionButtonsEnabled(false);
        centerPanel.add(toolbar, BorderLayout.SOUTH);

        add(centerPanel, BorderLayout.CENTER);

        // South: Transfer Progress Panel + Bottom Status Bar
        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(transferPanel, BorderLayout.CENTER);

        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(200, 200, 200)),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        statusBar.setFont(new Font("SansSerif", Font.PLAIN, 11));
        southPanel.add(statusBar, BorderLayout.SOUTH);

        add(southPanel, BorderLayout.SOUTH);
    }

    /**
     * Registers button listeners and window closing events.
     */
    private void setupListeners() {
        loginPanel.setLoginAction(this::handleLogin);
        loginPanel.setDisconnectAction(this::handleDisconnect);

        refreshButton.addActionListener(e -> refreshRemoteFiles());
        uploadButton.addActionListener(e -> handleUpload());
        downloadButton.addActionListener(e -> handleDownload());
        deleteButton.addActionListener(e -> handleDelete());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (client.isConnected()) {
                    client.disconnect();
                }
            }
        });
    }

    /**
     * Executes login request in a background thread.
     */
    private void handleLogin() {
        String host = loginPanel.getHost();
        int port = loginPanel.getPort();
        String username = loginPanel.getUsername();
        String password = loginPanel.getPassword();

        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Username and password cannot be empty.", "Validation Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        setFileActionButtonsEnabled(false);
        setStatus("Connecting to " + host + ":" + port + " as " + username + "...");

        new Thread(() -> {
            try {
                client.connect(host, port, username, password);
                loginPanel.setConnected(username);
                setStatus("Connected to " + host + ":" + port + " as " + username);
                setFileActionButtonsEnabled(true);
                refreshRemoteFiles();
            } catch (IOException e) {
                loginPanel.setDisconnected("Login Failed");
                setStatus("Connection failed: " + e.getMessage());
                JOptionPane.showMessageDialog(this, "Failed to connect or authenticate: " + e.getMessage(), "Connection Error", JOptionPane.ERROR_MESSAGE);
            }
        }, "FileTransfer-LoginThread").start();
    }

    /**
     * Executes disconnect in a background thread.
     */
    private void handleDisconnect() {
        new Thread(() -> {
            client.disconnect();
            loginPanel.setDisconnected("Disconnected");
            setFileActionButtonsEnabled(false);
            SwingUtilities.invokeLater(() -> tableModel.setFiles(null));
            setStatus("Disconnected from server");
        }, "FileTransfer-DisconnectThread").start();
    }

    /**
     * Refreshes the remote file list in a background thread.
     */
    private void refreshRemoteFiles() {
        if (!client.isConnected()) {
            return;
        }
        setStatus("Fetching remote files...");
        new Thread(() -> {
            try {
                List<FileInfo> files = client.listFiles();
                SwingUtilities.invokeLater(() -> {
                    tableModel.setFiles(files);
                    setStatus("Loaded " + files.size() + " remote file(s)");
                });
            } catch (IOException e) {
                setStatus("Failed to fetch file list: " + e.getMessage());
                JOptionPane.showMessageDialog(this, "Could not list files: " + e.getMessage(), "List Error", JOptionPane.ERROR_MESSAGE);
            }
        }, "FileTransfer-ListThread").start();
    }

    /**
     * Prompts for local file and initiates background upload.
     */
    private void handleUpload() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select File to Upload");
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File selectedFile = chooser.getSelectedFile();
        if (selectedFile == null || !selectedFile.isFile()) {
            return;
        }

        TransferTask task = new TransferTask(selectedFile.getName(), selectedFile.length(), true);
        transferPanel.startTransfer(task);
        setStatus("Starting upload: " + selectedFile.getName());

        new Thread(() -> {
            try {
                client.uploadFile(selectedFile, new TransferListener() {
                    @Override
                    public void onProgress(long transferredBytes, long totalBytes, double speedMBs, long etaSeconds) {
                        transferPanel.updateProgress(transferredBytes, totalBytes, speedMBs, etaSeconds);
                    }

                    @Override
                    public void onCompleted(String filename, String message) {
                        transferPanel.finishTransfer(filename, true, message);
                        setStatus("Upload completed: " + filename);
                        refreshRemoteFiles();
                    }

                    @Override
                    public void onError(String filename, String error) {
                        transferPanel.finishTransfer(filename, false, error);
                        setStatus("Upload failed: " + filename + " (" + error + ")");
                    }

                    @Override
                    public void onCancelled(String filename) {
                        transferPanel.finishTransfer(filename, false, "Cancelled by user");
                        setStatus("Upload cancelled: " + filename);
                    }
                }, task);
            } catch (IOException e) {
                if (!task.isCancelled()) {
                    transferPanel.finishTransfer(selectedFile.getName(), false, e.getMessage());
                    setStatus("Upload error: " + e.getMessage());
                }
            }
        }, "FileTransfer-UploadThread").start();
    }

    /**
     * Prompts for target folder and initiates background download of selected table file.
     */
    private void handleDownload() {
        int selectedRow = fileTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select a file from the table to download.", "No File Selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        FileInfo fileInfo = tableModel.getFileAt(selectedRow);
        if (fileInfo == null) {
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Destination Folder (Default: ./received)");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setSelectedFile(new File("received"));

        int res = chooser.showSaveDialog(this);
        File destDir;
        if (res == JFileChooser.APPROVE_OPTION) {
            destDir = chooser.getSelectedFile();
        } else {
            destDir = new File("received");
        }

        if (!destDir.exists()) {
            destDir.mkdirs();
        }

        TransferTask task = new TransferTask(fileInfo.getName(), fileInfo.getSize(), false);
        transferPanel.startTransfer(task);
        setStatus("Starting download: " + fileInfo.getName());

        new Thread(() -> {
            try {
                client.downloadFile(fileInfo.getName(), destDir, new TransferListener() {
                    @Override
                    public void onProgress(long transferredBytes, long totalBytes, double speedMBs, long etaSeconds) {
                        transferPanel.updateProgress(transferredBytes, totalBytes, speedMBs, etaSeconds);
                    }

                    @Override
                    public void onCompleted(String filename, String message) {
                        transferPanel.finishTransfer(filename, true, message);
                        setStatus("Download completed: " + filename + " saved to " + destDir.getPath());
                    }

                    @Override
                    public void onError(String filename, String error) {
                        transferPanel.finishTransfer(filename, false, error);
                        setStatus("Download failed: " + filename + " (" + error + ")");
                    }

                    @Override
                    public void onCancelled(String filename) {
                        transferPanel.finishTransfer(filename, false, "Cancelled by user");
                        setStatus("Download cancelled: " + filename);
                    }
                }, task);
            } catch (IOException e) {
                if (!task.isCancelled()) {
                    transferPanel.finishTransfer(fileInfo.getName(), false, e.getMessage());
                    setStatus("Download error: " + e.getMessage());
                }
            }
        }, "FileTransfer-DownloadThread").start();
    }

    /**
     * Prompts for confirmation and deletes selected file on server in a background thread.
     */
    private void handleDelete() {
        int selectedRow = fileTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select a file from the table to delete.", "No File Selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        FileInfo fileInfo = tableModel.getFileAt(selectedRow);
        if (fileInfo == null) {
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to delete '" + fileInfo.getName() + "' from server storage?",
                "Confirm Deletion",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        setStatus("Deleting " + fileInfo.getName() + "...");
        new Thread(() -> {
            try {
                client.deleteFile(fileInfo.getName());
                setStatus("Deleted file: " + fileInfo.getName());
                transferPanel.addHistory("[DELETED] " + fileInfo.getName());
                refreshRemoteFiles();
            } catch (IOException e) {
                setStatus("Delete failed: " + e.getMessage());
                JOptionPane.showMessageDialog(this, "Could not delete file: " + e.getMessage(), "Delete Error", JOptionPane.ERROR_MESSAGE);
            }
        }, "FileTransfer-DeleteThread").start();
    }

    /**
     * Enables or disables file manipulation buttons depending on active connection status.
     *
     * @param enabled true if connected
     */
    private void setFileActionButtonsEnabled(boolean enabled) {
        SwingUtilities.invokeLater(() -> {
            uploadButton.setEnabled(enabled);
            downloadButton.setEnabled(enabled);
            deleteButton.setEnabled(enabled);
            refreshButton.setEnabled(enabled);
        });
    }

    /**
     * Updates bottom status text thread-safely.
     *
     * @param text status message
     */
    private void setStatus(String text) {
        SwingUtilities.invokeLater(() -> statusBar.setText(" " + text));
    }

    /**
     * Application entry point for starting the Swing File Transfer client.
     *
     * @param args command line arguments (ignored)
     */
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        SwingUtilities.invokeLater(() -> {
            MainWindow window = new MainWindow();
            window.setVisible(true);
        });
    }
}
