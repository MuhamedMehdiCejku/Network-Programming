import java.awt.*;
import java.awt.event.ActionEvent;
import java.net.InetAddress;
import javax.swing.*;

public class MyDNSLookup {

    public static void main(String[] args) {

        JFrame frame = new JFrame("DNS Lookup Tool");
        frame.setSize(500, 400);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        // ---------- TOP PANEL ----------
        JPanel inputPanel = new JPanel(new FlowLayout());

        JLabel label = new JLabel("Domain / IP:");
        JTextField domainField = new JTextField(20);
        JButton lookupButton = new JButton("Lookup");

        JCheckBox reverseCheck = new JCheckBox("Reverse Lookup (IP)");
        JCheckBox reachabilityCheck = new JCheckBox("Check Reachability");

        inputPanel.add(label);
        inputPanel.add(domainField);
        inputPanel.add(lookupButton);
        inputPanel.add(reverseCheck);
        inputPanel.add(reachabilityCheck);

        // ---------- RESULT AREA ----------
        JTextArea resultArea = new JTextArea();
        resultArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(resultArea);

        frame.add(inputPanel, BorderLayout.NORTH);
        frame.add(scrollPane, BorderLayout.CENTER);

        // ---------- BUTTON ACTION ----------
        lookupButton.addActionListener((ActionEvent e) -> {

            String input = domainField.getText().trim();
            resultArea.setText("");

            if (input.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Please enter a domain name or IP address!");
                return;
            }

            try {
                long startTime = System.currentTimeMillis();

                // ---------- REVERSE LOOKUP ----------
                if (reverseCheck.isSelected()) {
                    InetAddress address = InetAddress.getByName(input);

                    resultArea.append("Reverse DNS Lookup\n");
                    resultArea.append("----------------------\n");
                    resultArea.append("IP Address: " + address.getHostAddress() + "\n");
                    resultArea.append("Host Name: " + address.getHostName() + "\n");
                    resultArea.append("Canonical Name: " + address.getCanonicalHostName() + "\n");

                    if (reachabilityCheck.isSelected()) {
                        boolean reachable = address.isReachable(3000);
                        resultArea.append("Reachable: " + (reachable ? "YES" : "NO") + "\n");
                    }

                } 
                // ---------- NORMAL DNS LOOKUP ----------
                else {

                    if (!input.contains(".")) {
                        input = input + ".com";
                    }

                    InetAddress[] addresses = InetAddress.getAllByName(input);

                    resultArea.append("DNS Lookup Results\n");
                    resultArea.append("----------------------\n");

                    for (InetAddress addr : addresses) {
                        resultArea.append("Domain: " + input + "\n");
                        resultArea.append("IP Address: " + addr.getHostAddress() + "\n");
                        resultArea.append("Host Name: " + addr.getHostName() + "\n");
                        resultArea.append("Canonical Name: " + addr.getCanonicalHostName() + "\n");

                        if (reachabilityCheck.isSelected()) {
                            boolean reachable = addr.isReachable(3000);
                            resultArea.append("Reachable: " + (reachable ? "YES" : "NO") + "\n");
                        }

                        resultArea.append("----------------------\n");
                    }
                }

                long endTime = System.currentTimeMillis();
                resultArea.append("DNS Lookup Time: " + (endTime - startTime) + " ms\n");

            } catch (Exception ex) {
                resultArea.setText("Error: Domain or IP not found, or network access blocked.");
            }
        });

        frame.setVisible(true);
    }
}