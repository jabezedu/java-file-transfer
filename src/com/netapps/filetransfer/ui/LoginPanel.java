package com.netapps.filetransfer.ui;

import com.netapps.filetransfer.common.ProtocolConstants;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * Top toolbar panel managing server connection inputs (host, port, credentials)
 * and active session connection state.
 */
public class LoginPanel extends JPanel {

    private final JTextField hostField = new JTextField("127.0.0.1", 9);
    private final JTextField portField = new JTextField(String.valueOf(ProtocolConstants.DEFAULT_PORT), 4);
    private final JTextField usernameField = new JTextField("demo", 7);
    private final JPasswordField passwordField = new JPasswordField("demo123", 7);
    private final JButton loginButton = new JButton("Login");
    private final JButton disconnectButton = new JButton("Disconnect");
    private final JLabel statusLabel = new JLabel("Not Connected");

    private Runnable loginAction;
    private Runnable disconnectAction;

    /**
     * Constructs the LoginPanel.
     */
    public LoginPanel() {
        setLayout(new FlowLayout(FlowLayout.LEFT, 8, 6));
        setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(200, 200, 200)));

        add(new JLabel("Host:"));
        add(hostField);

        add(new JLabel("Port:"));
        add(portField);

        add(new JLabel("User:"));
        add(usernameField);

        add(new JLabel("Pass:"));
        add(passwordField);

        add(loginButton);
        add(disconnectButton);
        disconnectButton.setEnabled(false);

        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        statusLabel.setForeground(new Color(120, 120, 120));
        add(statusLabel);

        loginButton.addActionListener(e -> {
            if (loginAction != null) {
                loginAction.run();
            }
        });

        disconnectButton.addActionListener(e -> {
            if (disconnectAction != null) {
                disconnectAction.run();
            }
        });
    }

    /**
     * Sets callback invoked when login button is pressed.
     *
     * @param action runnable callback
     */
    public void setLoginAction(Runnable action) {
        this.loginAction = action;
    }

    /**
     * Sets callback invoked when disconnect button is pressed.
     *
     * @param action runnable callback
     */
    public void setDisconnectAction(Runnable action) {
        this.disconnectAction = action;
    }

    /**
     * Retrieves entered server host string.
     *
     * @return host string
     */
    public String getHost() {
        return hostField.getText().trim();
    }

    /**
     * Retrieves entered port number.
     *
     * @return port integer
     */
    public int getPort() {
        try {
            return Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            return ProtocolConstants.DEFAULT_PORT;
        }
    }

    /**
     * Retrieves entered username.
     *
     * @return username string
     */
    public String getUsername() {
        return usernameField.getText().trim();
    }

    /**
     * Retrieves entered password.
     *
     * @return password string
     */
    public String getPassword() {
        return new String(passwordField.getPassword());
    }

    /**
     * Updates UI controls for an established session.
     *
     * @param username authenticated user
     */
    public void setConnected(String username) {
        SwingUtilities.invokeLater(() -> {
            hostField.setEnabled(false);
            portField.setEnabled(false);
            usernameField.setEnabled(false);
            passwordField.setEnabled(false);
            loginButton.setEnabled(false);
            disconnectButton.setEnabled(true);
            statusLabel.setText("Connected as: " + username);
            statusLabel.setForeground(new Color(46, 125, 50));
        });
    }

    /**
     * Updates UI controls for a disconnected state.
     *
     * @param message status notice
     */
    public void setDisconnected(String message) {
        SwingUtilities.invokeLater(() -> {
            hostField.setEnabled(true);
            portField.setEnabled(true);
            usernameField.setEnabled(true);
            passwordField.setEnabled(true);
            loginButton.setEnabled(true);
            disconnectButton.setEnabled(false);
            statusLabel.setText(message != null ? message : "Disconnected");
            statusLabel.setForeground(new Color(180, 40, 40));
        });
    }
}
