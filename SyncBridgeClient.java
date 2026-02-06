package sb.client;

import javafx.animation.Animation; 
import javafx.animation.KeyFrame;   
import javafx.animation.Timeline;   
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime; 
import java.time.format.DateTimeFormatter; 

public class SyncBridgeClient extends Application {

    private ListView<String> chatList;
    private TextField input, hostField;
    private Label status;
    private Label centerInfo;      
    private Label dateTimeLabel; 

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private Thread readerThread;

    private String nick = "User";
    private String email = "";
    private boolean typing = false;
    private boolean hasSentRead = false; 
    private String lastReadMessage = ""; 

    private Stage mainStage;
    private PauseTransition infoHideTimer;

    @Override
    public void start(Stage stage) {
        if (!showLogin()) {
            Platform.exit();
            return;
        }
        mainStage = stage;

        chatList = new ListView<>();
        chatList.setCellFactory(list -> new ChatCell());

        input = new TextField();
        input.setPromptText("Type a message and press Enter...");

        hostField = new TextField("127.0.0.1");
        hostField.setPrefWidth(130);

        status = new Label("Disconnected");
        status.setStyle("-fx-text-fill:#fecaca;");
        
        // --- Live Date and Time Setup ---
        DateTimeFormatter dateTimeFormat = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss");
        dateTimeLabel = new Label();
        dateTimeLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        
        // Timer to update the time every second
        Timeline timeline = new Timeline(
            new KeyFrame(Duration.seconds(0), 
                e -> dateTimeLabel.setText("Time: " + LocalDateTime.now().format(dateTimeFormat))),
            new KeyFrame(Duration.seconds(1))
        );
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
        // --- End Live Date and Time Setup ---

        Button connectBtn = new Button("Connect");
        Button sendBtn    = new Button("Send");
        Button saveBtn    = new Button("Save Chat");
        
        styleButton(connectBtn, "#9333ea", "#7c3aed"); 
        styleButton(sendBtn, "#9333ea", "#7c3aed");   
        styleButton(saveBtn, "#9333ea", "#7c3aed");   

        connectBtn.setOnAction(e -> connect());
        sendBtn.setOnAction(e -> send());
        input.setOnAction(e -> send());
        saveBtn.setOnAction(e -> saveChat());

        input.setOnKeyTyped(e -> {
            if (out == null) return;
            String txt = input.getText();
            if (!typing && txt != null && !txt.trim().isEmpty()) {
                out.println("TYPING_ON");
                typing = true;
                clearCenter(false); // Clear read status when typing starts
            }
            if (typing) {
                infoHideTimer.playFromStart();
            }
        });

        // READ logic: Send READ command when chat is interacted with and a new message exists
        chatList.setOnMouseClicked(e -> {
            if (out != null && !hasSentRead) {
                out.println("READ");
                hasSentRead = true;
            }
        });
        
        chatList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && out != null && !hasSentRead) {
                 out.println("READ");
                 hasSentRead = true;
            }
        });


        centerInfo = new Label();
        centerInfo.setStyle("-fx-text-fill:#a78bfa;-fx-font-size:11; -fx-font-weight: bold;"); 
        centerInfo.setMaxWidth(Double.MAX_VALUE);
        centerInfo.setAlignment(Pos.CENTER);
        centerInfo.setVisible(false);

        infoHideTimer = new PauseTransition(Duration.seconds(3));
        infoHideTimer.setOnFinished(e -> {
            // Only hide typing status automatically
            if (centerInfo.getText().contains("typing")) { 
                clearCenter(false); 
                // Re-display persistent read status if it existed
                if (!lastReadMessage.isEmpty()) {
                    showCenter(lastReadMessage, false);
                }
            }
        });

        HBox top = new HBox(8,
                new Label("Server IP:"), hostField,
                connectBtn, saveBtn, status, dateTimeLabel); 
        HBox.setHgrow(dateTimeLabel, Priority.ALWAYS);
        dateTimeLabel.setAlignment(Pos.CENTER_RIGHT);
        
        top.setPadding(new Insets(8));
        top.setStyle(
            "-fx-background-color:linear-gradient(to right,#1e40af,#3b82f6);" 
        );

        VBox centerBox = new VBox(chatList);
        centerBox.setPadding(new Insets(8));
        centerBox.setStyle("-fx-background-color:#020617;"); 

        HBox bottomBar = new HBox(8, input, sendBtn);
        bottomBar.setPadding(new Insets(8));
        HBox.setHgrow(input, Priority.ALWAYS);

        StackPane infoPane = new StackPane(centerInfo);
        infoPane.setAlignment(Pos.CENTER);
        infoPane.setPadding(new Insets(0, 8, 0, 8));

        VBox bottom = new VBox(4, infoPane, bottomBar);

        BorderPane root = new BorderPane();
        root.setTop(top);
        root.setCenter(centerBox);
        root.setBottom(bottom);

        Scene scene = new Scene(root, 720, 460);
        stage.setTitle("SyncBridge Client – " + nick);
        stage.setScene(scene);
        stage.show();

        stage.setOnCloseRequest(e -> closeConn());
    }

    private boolean showLogin() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("SyncBridge Login");
        dialog.setHeaderText("Enter your full name and university e-mail");

        DialogPane pane = dialog.getDialogPane();
        pane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        pane.setStyle(
            "-fx-background-color:linear-gradient(to bottom,#020617,#111827);" +
            "-fx-text-fill:white;"
        );

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(15));

        Label nameLabel  = new Label("Full name:");
        Label mailLabel  = new Label("E-mail:");
        nameLabel.setStyle("-fx-text-fill:white;");
        mailLabel.setStyle("-fx-text-fill:white;");

        TextField nameField  = new TextField();
        nameField.setPromptText("Your full name");
        TextField emailField = new TextField("your.name@uni-prizren.com");
        emailField.setPromptText("your.name@uni-prizren.com");

        String tfStyle =
            "-fx-background-color:#020617;" +
            "-fx-text-fill:white;" +
            "-fx-prompt-text-fill:#64748b;" +
            "-fx-border-color:#3b82f6;" + 
            "-fx-border-radius:6;" +
            "-fx-background-radius:6;";

        nameField.setStyle(tfStyle);
        emailField.setStyle(tfStyle);

        grid.addRow(0, nameLabel, nameField);
        grid.addRow(1, mailLabel, emailField);

        pane.setContent(grid);

        Button okBtn = (Button) pane.lookupButton(ButtonType.OK);
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, evt -> {
            String n  = nameField.getText().trim();
            String em = emailField.getText().trim().toLowerCase();
            if (n.isEmpty() || em.isEmpty() ||
                    !em.endsWith("@uni-prizren.com") || !em.contains("@")) {
                new Alert(Alert.AlertType.ERROR,
                        "Please enter your full name and a valid @uni-prizren.com e-mail.",
                        ButtonType.OK).showAndWait();
                evt.consume();
            } else {
                nick = n;
                email = em;
            }
        });

        var res = dialog.showAndWait();
        return res.isPresent() && res.get() == ButtonType.OK;
    }

    private void styleButton(Button b, String startColor, String endColor) {
        b.setStyle(
            "-fx-background-color:linear-gradient(to right," + startColor + "," + endColor + ");" +
            "-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:10;"
        );
    }
    
    private void connect() {
        String hostTxt = hostField.getText().trim();
        final String host = hostTxt.isEmpty() ? "127.0.0.1" : hostTxt;
        final int port = 5050;

        if (socket != null && socket.isConnected() && !socket.isClosed()) {
            addLine("[system] Already connected.");
            return;
        }

        Thread t = new Thread(() -> {
            try {
                socket = new Socket(host, port);
                in  = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                out = new PrintWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

                out.println("NICK:" + nick);
                
                hasSentRead = false; 

                Platform.runLater(() -> {
                    status.setText("Connected");
                    status.setStyle("-fx-text-fill:#bbf7d0;");
                    chatList.getItems().clear(); 
                    addLine("[system] Connected to " + host + ":" + port +
                            " as " + nick + " (" + email + ")");
                });

                readerThread = new Thread(() -> {
                    try {
                        String line;
                        while ((line = in.readLine()) != null) {
                            final String msg = line;
                            Platform.runLater(() -> handleIncoming(msg));
                        }
                    } catch (IOException e) {
                        Platform.runLater(() ->
                                addLine("[system] Connection closed."));
                    }
                }, "sb-reader");
                readerThread.setDaemon(true);
                readerThread.start();

            } catch (IOException e) {
                Platform.runLater(() -> {
                    status.setText("Disconnected");
                    status.setStyle("-fx-text-fill:#fecaca;");
                    addLine("[error] Could not connect: " + e.getMessage());
                });
            }
        }, "sb-connector");
        t.setDaemon(true);
        t.start();
    }

    private void handleIncoming(String msg) {
        
        if (msg.startsWith("[typing]")) {
            String who = msg.substring("[typing]".length()).trim();
            showCenter(who, true);
            return;
        }

        if (msg.startsWith("[read]")) {
            String readMsg = msg.substring("[read]".length()).trim();
            lastReadMessage = readMsg;
            showCenter(readMsg, false);
            return;
        }
        
        // This is the trigger to reset the 'read' status only if a new chat message arrives
        if (!msg.startsWith("[system]") && msg.matches(".*: .*") && !msg.startsWith(nick + ": ")) {
            hasSentRead = false; 
            clearCenter(true); 
        }

        // Add the message to the chat list (This correctly handles the SAVE_CHAT broadcast from the server)
        addLine(msg);
    }

    private void showCenter(String text, boolean autoHide) {
        centerInfo.setText(text);
        centerInfo.setVisible(true);
        if (autoHide) {
            infoHideTimer.stop();
            infoHideTimer.playFromStart();
        }
    }

    private void clearCenter(boolean resetPersistentReadStatus) {
        infoHideTimer.stop();
        centerInfo.setText("");
        centerInfo.setVisible(false);
        if (resetPersistentReadStatus) {
            lastReadMessage = "";
        }
    }

    private void send() {
        String txt = input.getText().trim();
        if (txt.isEmpty()) return;
        if (out == null) {
            addLine("[error] Not connected to server.");
            return;
        }
        
        // 1. Send message
        out.println("MSG:" + txt);
        addLine(nick + ": " + txt);
        input.clear();

        // 2. Clear typing status if active
        if (typing) {
            out.println("TYPING_OFF");
            typing = false;
        }
        
        // 3. Reset persistent read status
        lastReadMessage = "";
        clearCenter(false); 
    }

    private void saveChat() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save Chat");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Text Files (*.txt)", "*.txt"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        fc.setInitialFileName("syncbridge_chat.txt");
        File f = fc.showSaveDialog(mainStage);
        if (f == null) return;
        
        String filename = f.getName();
        
        try (PrintWriter pw = new PrintWriter(new FileWriter(f, false))) {
            for (String s : chatList.getItems()) pw.println(s);
            
            // Send save command to server. The server will broadcast the confirmation 
            // back to ALL clients, including this one.
            if (out != null) {
                out.println("SAVE_CHAT:" + filename);
            }
            
        } catch (IOException e) {
            addLine("[error] Could not save chat: " + e.getMessage());
        }
    }

    private void addLine(String s) {
        chatList.getItems().add(s);
        chatList.scrollTo(chatList.getItems().size() - 1);
    }

    private void closeConn() {
        try {
            if (out != null) out.println("QUIT");
        } catch (Exception ignored) {}
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
        if (readerThread != null && readerThread.isAlive()) readerThread.interrupt();
    }

    private class ChatCell extends ListCell<String> {
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }

            Label label = new Label(item);
            label.setWrapText(true);
            label.setMaxWidth(400);
            HBox box = new HBox(label);
            box.setPadding(new Insets(4));

            if (item.startsWith(nick + ": ")) { 
                // Sender (Your Client): Blue
                label.setStyle(
                    "-fx-background-color:#3b82f6;" + 
                    "-fx-text-fill:white;" +
                    "-fx-padding:6 10 6 10;" +
                    "-fx-background-radius:12;"
                );
                box.setAlignment(Pos.CENTER_RIGHT);
            } else if (item.startsWith("[system]") || item.startsWith("[error]")) {
                // System messages: Light Purple
                label.setStyle("-fx-text-fill:#a78bfa; -fx-font-size:11; -fx-font-weight: bold;");
                box.setAlignment(Pos.CENTER);
            } else { 
                // Receiver (Other User): Teal/Cyan
                label.setStyle(
                    "-fx-background-color:#14b8a6;" + 
                    "-fx-text-fill:white;" +
                    "-fx-padding:6 10 6 10;" +
                    "-fx-background-radius:12;"
                );
                box.setAlignment(Pos.CENTER_LEFT);
            }
            setGraphic(box);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}