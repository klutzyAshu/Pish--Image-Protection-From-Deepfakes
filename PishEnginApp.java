package com.pishengin;

import javafx.animation.FadeTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PishEnginApp extends Application {

    private File selectedFile;
    private File outputFile;
    private final ImageView beforeView = new ImageView();
    private final ImageView afterView = new ImageView();
    private final Label statusLabel = new Label("Drop an image or click Select Photo");
    private final ProgressBar progressBar = new ProgressBar(0);
    private final TextArea logArea = new TextArea();
    private final ToggleGroup strengthGroup = new ToggleGroup();
    private CheckBox faceCheck;
    private Slider stepsSlider;
    private Button runButton;
    private Button saveButton;

    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-pane");

        root.setTop(buildHeader());
        root.setCenter(buildCenter());
        root.setBottom(buildFooter());

        Scene scene = new Scene(root, 1100, 720);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());

        stage.setTitle("Pish-Engin");
        stage.setScene(scene);
        stage.setMinWidth(420);
        stage.setMinHeight(600);
        stage.show();

        root.requestFocus();
        fadeIn(root);
    }

    private Node buildHeader() {
        Label title = new Label("Pish-Engin");
        title.getStyleClass().add("app-title");

        Label subtitle = new Label("Anti-AI Image Cloak");
        subtitle.getStyleClass().add("app-subtitle");

        VBox titleBox = new VBox(2, title, subtitle);

        HBox header = new HBox(titleBox);
        header.getStyleClass().add("header-bar");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(24, 32, 24, 32));
        return header;
    }

    private Node buildCenter() {
        VBox dropZone = buildDropZone();

        VBox controls = buildControlsPanel();

        HBox previews = new HBox(20, buildPreviewCard("Original", beforeView), buildPreviewCard("Protected", afterView));
        previews.setAlignment(Pos.CENTER);
        previews.setPadding(new Insets(0, 0, 20, 0));

        VBox centerStack = new VBox(20, dropZone, previews, controls);
        centerStack.setPadding(new Insets(10, 32, 10, 32));
        centerStack.getStyleClass().add("center-stack");

        ScrollPane scroll = new ScrollPane(centerStack);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("edge-to-edge");
        scroll.setVvalue(0.0);
        Platform.runLater(() -> scroll.setVvalue(0.0));
        return scroll;
    }

    private VBox buildDropZone() {
        Label icon = new Label("+");
        icon.getStyleClass().add("drop-icon");

        Label hint = new Label("Drag & drop an image, or click to browse");
        hint.getStyleClass().add("drop-hint");

        VBox box = new VBox(8, icon, hint);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(36));
        box.getStyleClass().add("drop-zone");

        box.setOnDragOver(e -> {
            if (e.getDragboard().hasFiles()) e.acceptTransferModes(TransferMode.COPY);
            e.consume();
        });
        box.setOnDragDropped(e -> {
            Dragboard db = e.getDragboard();
            if (db.hasFiles()) {
                loadImage(db.getFiles().get(0));
                e.setDropCompleted(true);
            }
            e.consume();
        });
        box.setOnMouseClicked(e -> pickFile(box.getScene().getWindow()));

        return box;
    }

    private VBox buildPreviewCard(String label, ImageView view) {
        view.setFitWidth(300);
        view.setFitHeight(300);
        view.setPreserveRatio(true);
        view.setSmooth(true);

        StackPane frame = new StackPane(view);
        frame.getStyleClass().add("preview-frame");
        frame.setPrefSize(320, 320);

        Label lbl = new Label(label);
        lbl.getStyleClass().add("preview-label");

        VBox card = new VBox(10, frame, lbl);
        card.setAlignment(Pos.CENTER);
        return card;
    }

    private VBox buildControlsPanel() {
        Label strengthLabel = new Label("Protection Strength");
        strengthLabel.getStyleClass().add("field-label");

        HBox strengthRow = new HBox(8,
                strengthToggle("Low", "low"),
                strengthToggle("Medium", "medium"),
                strengthToggle("High", "high"));
        ((ToggleButton) strengthGroup.getToggles().get(1)).setSelected(true);

        Label stepsLabel = new Label("Optimization Steps: 40");
        stepsLabel.getStyleClass().add("field-label");
        stepsSlider = new Slider(10, 100, 40);
        stepsSlider.setShowTickMarks(false);
        stepsSlider.valueProperty().addListener((obs, oldV, newV) ->
                stepsLabel.setText("Optimization Steps: " + newV.intValue()));
        stepsSlider.getStyleClass().add("modern-slider");

        faceCheck = new CheckBox("Also protect against face-swap deepfakes");
        faceCheck.getStyleClass().add("modern-check");

        runButton = new Button("Protect Photo");
        runButton.getStyleClass().add("primary-button");
        runButton.setMaxWidth(Double.MAX_VALUE);
        runButton.setOnAction(e -> runProtection());
        runButton.setDisable(true);

        saveButton = new Button("Save As...");
        saveButton.getStyleClass().add("secondary-button");
        saveButton.setMaxWidth(Double.MAX_VALUE);
        saveButton.setDisable(true);
        saveButton.setOnAction(e -> saveOutput());

        HBox buttonRow = new HBox(12, runButton, saveButton);
        HBox.setHgrow(runButton, Priority.ALWAYS);
        HBox.setHgrow(saveButton, Priority.ALWAYS);

        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.getStyleClass().add("modern-progress");

        logArea.setEditable(false);
        logArea.setPrefRowCount(5);
        logArea.getStyleClass().add("log-area");

        TitledPane logPane = new TitledPane("Details", logArea);
        logPane.setExpanded(false);
        logPane.getStyleClass().add("log-pane");

        VBox panel = new VBox(14,
                strengthLabel, strengthRow,
                stepsLabel, stepsSlider,
                faceCheck,
                buttonRow,
                statusLabel,
                progressBar,
                logPane);
        panel.getStyleClass().add("controls-panel");
        panel.setPadding(new Insets(24));
        panel.setMaxWidth(560);
        VBox wrapper = new VBox(panel);
        wrapper.setAlignment(Pos.CENTER);
        return wrapper;
    }

    private ToggleButton strengthToggle(String label, String value) {
        ToggleButton btn = new ToggleButton(label);
        btn.setUserData(value);
        btn.setToggleGroup(strengthGroup);
        btn.getStyleClass().add("segmented-button");
        HBox.setHgrow(btn, Priority.ALWAYS);
        btn.setMaxWidth(Double.MAX_VALUE);
        return btn;
    }

    private Node buildFooter() {
        Label note = new Label("Raises the cost of AI misuse - not a guarantee against every model.");
        note.getStyleClass().add("footer-note");
        HBox footer = new HBox(note);
        footer.setAlignment(Pos.CENTER);
        footer.setPadding(new Insets(10));
        footer.getStyleClass().add("footer-bar");
        return footer;
    }

    private void pickFile(javafx.stage.Window window) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Photo to Protect");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.webp", "*.bmp"));
        File file = chooser.showOpenDialog(window);
        if (file != null) loadImage(file);
    }

    private void loadImage(File file) {
        selectedFile = file;
        beforeView.setImage(new Image(file.toURI().toString(), 300, 300, true, true, true));
        afterView.setImage(null);
        statusLabel.setText("Ready: " + file.getName());
        runButton.setDisable(false);
        saveButton.setDisable(true);
    }

    private void runProtection() {
        if (selectedFile == null) return;

        String strength = (String) strengthGroup.getSelectedToggle().getUserData();
        int steps = (int) stepsSlider.getValue();
        boolean face = faceCheck.isSelected();

        Path outDir = selectedFile.toPath().getParent();
        String outName = stripExt(selectedFile.getName()) + "_pish_engin.png";
        outputFile = outDir.resolve(outName).toFile();

        runButton.setDisable(true);
        saveButton.setDisable(true);
        progressBar.setProgress(0);
        logArea.clear();
        statusLabel.setText("Running protection...");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                List<String> cmd = new java.util.ArrayList<>(List.of(
                        pythonExecutable(), "Pish-Engin.py",
                        "-i", selectedFile.getAbsolutePath(),
                        "-o", outputFile.getAbsolutePath(),
                        "-s", strength,
                        "--steps", String.valueOf(steps)));
                if (face) cmd.add("--face");

                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.redirectErrorStream(true);
                Process process = pb.start();

                Pattern stepPattern = Pattern.compile("(\\d+)/(\\d+)");
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String finalLine = line;
                        Platform.runLater(() -> logArea.appendText(finalLine + "\n"));
                        Matcher m = stepPattern.matcher(line);
                        if (m.find()) {
                            double current = Double.parseDouble(m.group(1));
                            double total = Double.parseDouble(m.group(2));
                            updateProgress(current, total);
                        }
                    }
                }
                process.waitFor();
                return null;
            }
        };

        progressBar.progressProperty().bind(task.progressProperty());

        task.setOnSucceeded(e -> {
            progressBar.progressProperty().unbind();
            progressBar.setProgress(1);
            if (outputFile.exists()) {
                afterView.setImage(new Image(outputFile.toURI().toString(), 300, 300, true, true, true));
                statusLabel.setText("Done: " + outputFile.getName());
                saveButton.setDisable(false);
            } else {
                statusLabel.setText("Finished, but output file was not found.");
            }
            runButton.setDisable(false);
        });

        task.setOnFailed(e -> {
            progressBar.progressProperty().unbind();
            statusLabel.setText("Failed - check details below.");
            logArea.appendText("\nError: " + task.getException() + "\n");
            runButton.setDisable(false);
        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private void saveOutput() {
        if (outputFile == null || !outputFile.exists()) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Protected Photo");
        chooser.setInitialFileName(outputFile.getName());
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG Image", "*.png"));
        File dest = chooser.showSaveDialog(saveButton.getScene().getWindow());
        if (dest != null) {
            try {
                java.nio.file.Files.copy(outputFile.toPath(), dest.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                statusLabel.setText("Saved to " + dest.getName());
            } catch (Exception ex) {
                statusLabel.setText("Save failed: " + ex.getMessage());
            }
        }
    }

    private String pythonExecutable() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("win") ? "python" : "python3";
    }

    private String stripExt(String name) {
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(0, i) : name;
    }

    private void fadeIn(Node node) {
        FadeTransition ft = new FadeTransition(Duration.millis(400), node);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
