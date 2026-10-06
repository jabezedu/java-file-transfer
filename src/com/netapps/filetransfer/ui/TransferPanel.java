package com.netapps.filetransfer.ui;

import com.netapps.filetransfer.client.TransferTask;
import com.netapps.filetransfer.util.FormatUtil;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/**
 * Bottom dashboard panel displaying the active transfer progress bar,
 * live throughput rate, ETA counter, cancellation button, and transfer history list.
 */
public class TransferPanel extends JPanel {

    private final JProgressBar progressBar;
    private final JLabel statusLabel;
    private final JLabel rateEtaLabel;
    private final JButton cancelButton;
    private final DefaultListModel<String> historyModel;
    private final JList<String> historyList;

    private volatile TransferTask activeTask;

    /**
     * Constructs the TransferPanel layout.
     */
    public TransferPanel() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createTitledBorder("Transfers"));
        setPreferredSize(new Dimension(0, 180));

        // Active transfer controls (North/Center)
        JPanel activePanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 6, 3, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Row 0: Status label
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        statusLabel = new JLabel("Idle - No active transfer");
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        activePanel.add(statusLabel, gbc);

        // Row 1: Progress bar + Cancel button
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 1.0;
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setString("0%");
        progressBar.setPreferredSize(new Dimension(0, 22));
        activePanel.add(progressBar, gbc);

        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 0.0;
        cancelButton = new JButton("Cancel");
        cancelButton.setEnabled(false);
        cancelButton.addActionListener(e -> cancelActiveTransfer());
        activePanel.add(cancelButton, gbc);

        // Row 2: Rate and ETA label
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        rateEtaLabel = new JLabel("Speed: 0.00 MB/s | ETA: --:--");
        rateEtaLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));
        rateEtaLabel.setForeground(new Color(100, 100, 100));
        activePanel.add(rateEtaLabel, gbc);

        add(activePanel, BorderLayout.NORTH);

        // Transfer history (Center/South)
        historyModel = new DefaultListModel<>();
        historyList = new JList<>(historyModel);
        historyList.setFont(new Font("SansSerif", Font.PLAIN, 11));

        JScrollPane historyScroll = new JScrollPane(historyList);
        historyScroll.setBorder(BorderFactory.createTitledBorder("Transfer History"));
        add(historyScroll, BorderLayout.CENTER);
    }

    /**
     * Initializes UI state for an active upload or download task.
     *
     * @param task the TransferTask to observe
     */
    public void startTransfer(TransferTask task) {
        this.activeTask = task;
        SwingUtilities.invokeLater(() -> {
            cancelButton.setEnabled(true);
            progressBar.setValue(0);
            progressBar.setString("0%");
            String direction = task.isUpload() ? "Uploading" : "Downloading";
            statusLabel.setText(direction + ": " + task.getFilename() + " (" + FormatUtil.formatByteSize(task.getTotalBytes()) + ")");
            rateEtaLabel.setText("Speed: calculating... | ETA: calculating...");
        });
    }

    /**
     * Updates progress bar, speed, and ETA during transfer.
     *
     * @param transferred current transferred bytes
     * @param total       total file size
     * @param speedMBs    current speed in MB/s
     * @param etaSec      estimated remaining time in seconds
     */
    public void updateProgress(long transferred, long total, double speedMBs, long etaSec) {
        SwingUtilities.invokeLater(() -> {
            int percent = total > 0 ? (int) ((transferred * 100) / total) : 0;
            progressBar.setValue(percent);
            progressBar.setString(percent + "% (" + FormatUtil.formatByteSize(transferred) + " / " + FormatUtil.formatByteSize(total) + ")");
            rateEtaLabel.setText("Speed: " + FormatUtil.formatSpeed(speedMBs) + " | ETA: " + FormatUtil.formatEta(etaSec));
        });
    }

    /**
     * Completes active transfer and logs it into history.
     *
     * @param filename file name
     * @param success  true if completed successfully
     * @param details  extra description
     */
    public void finishTransfer(String filename, boolean success, String details) {
        this.activeTask = null;
        SwingUtilities.invokeLater(() -> {
            cancelButton.setEnabled(false);
            if (success) {
                progressBar.setValue(100);
                progressBar.setString("100% Complete");
                statusLabel.setText("Finished: " + filename);
                rateEtaLabel.setText("Status: Success - " + details);
                addHistory("[SUCCESS] " + filename + " - " + details);
            } else {
                progressBar.setValue(0);
                progressBar.setString("Failed");
                statusLabel.setText("Failed: " + filename);
                rateEtaLabel.setText("Error: " + details);
                addHistory("[FAILED] " + filename + " - " + details);
            }
        });
    }

    /**
     * Cancels the running task if active.
     */
    private void cancelActiveTransfer() {
        if (activeTask != null) {
            activeTask.cancel();
            cancelButton.setEnabled(false);
            statusLabel.setText("Cancelling transfer...");
        }
    }

    /**
     * Appends an entry into the transfer history list.
     *
     * @param entry text to display
     */
    public void addHistory(String entry) {
        SwingUtilities.invokeLater(() -> {
            historyModel.addElement(entry);
            historyList.ensureIndexIsVisible(historyModel.getSize() - 1);
        });
    }

    /**
     * Returns the active transfer task or null if idle.
     *
     * @return active task or null
     */
    public TransferTask getActiveTask() {
        return activeTask;
    }
}
