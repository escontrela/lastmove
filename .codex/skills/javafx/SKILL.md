---
name: javafx-springboot-app-design-guide
description: "Use when: need a full design guide to build a JavaFX desktop app with Spring Boot, Maven, DDD/hex architecture, and the UiScreen wrapper system used in Tasker."
---

# JavaFX + Spring Boot App Design Guide (Tasker Pattern)

## Purpose
This guide describes how the Tasker application is built and how to replicate the same design for any JavaFX + Spring Boot desktop app. It focuses on:
- Libraries and Maven layout
- Package structure and architecture (DDD + hexagonal)
- JavaFX windowing and UI wrapper system (UiScreen, UiScreenFactory, UiFlowManager)
- Data flow between controllers and application/domain layers
- Practical code examples taken from the project

The goal is to allow any AI or developer to build a similar app by following these patterns.

---

## 1) Libraries and stack

### Core runtime
- Java 22 (project property `java.version`)
- Spring Boot 3.3.2 (parent BOM)
- JavaFX 21 (OpenJFX)

### Persistence
- SQLite JDBC driver
- Flyway for migrations
- HikariCP (via spring-boot-starter-jdbc)

### UI and Desktop
- JavaFX controls + FXML
- JavaFX styles via CSS

### Supporting
- Spring Cache + Caffeine
- Spring Validation
- Spring AI + MCP (optional for this app)
- springdoc-openapi (for API documentation)

### Testing
- spring-boot-starter-test (JUnit 5, Mockito, etc.)

---

## 2) Maven layout and build conventions

The project uses a standard Maven + Spring Boot structure, with `spring-boot-starter-parent` providing dependency management (BOM). There is no custom archetype defined in this repository. Use the standard Spring Boot Maven layout and apply the same modules and packaging decisions.

Key POM excerpts:

```xml
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>3.3.2</version>
</parent>

<properties>
  <java.version>22</java.version>
  <javafx.version>21.0.9</javafx.version>
</properties>

<dependencies>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jdbc</artifactId>
  </dependency>
  <dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
  </dependency>
  <dependency>
    <groupId>org.xerial</groupId>
    <artifactId>sqlite-jdbc</artifactId>
    <version>3.45.3.0</version>
  </dependency>
  <dependency>
    <groupId>org.openjfx</groupId>
    <artifactId>javafx-controls</artifactId>
    <version>${javafx.version}</version>
  </dependency>
  <dependency>
    <groupId>org.openjfx</groupId>
    <artifactId>javafx-fxml</artifactId>
    <version>${javafx.version}</version>
  </dependency>
</dependencies>
```

Recommended build plugins:
- `spring-boot-maven-plugin`
- `maven-compiler-plugin` with `<release>${java.version}</release>`
- `maven-surefire-plugin`

---

## 3) Package structure and screaming architecture

The project is organized so the architecture is visible at first glance.

```
src/main/java/com/davidpe/tasker
  bootstrap/      -> app bootstrap (JavaFX + Spring Boot wiring)
  api/            -> REST controllers (Spring MVC)
  application/    -> use cases, UI orchestration, services
  domain/         -> entities, value objects, domain events
  infrastructure/ -> JDBC repos, DB config, event bus impl
  ui/             -> JavaFX controllers and UI logic
```

### DDD and hexagonal layering
- `domain`: pure business rules, entities, value objects, domain events. No Spring or JavaFX dependencies.
- `application`: use cases and orchestration. Defines interfaces for repositories or event buses.
- `infrastructure`: implements persistence and event bus using Spring + JDBC.
- `api`: thin REST endpoints and DTOs, maps to application use cases.
- `ui`: JavaFX controllers, presenters, and UI-specific data.

This structure supports hexagonal architecture: the domain and application layers are the core, and adapters live in infrastructure, api, and ui.

---

## 3.1) Resources structure

The `src/main/resources` folder is structured to mirror the UI and DB concerns. Keep it predictable and stable so both JavaFX and Spring can resolve assets reliably.

```
src/main/resources
  application.yml
  db/
    migration/
      V1__init.sql
      V2__projects_priorities_tags_tasks.sql
      ...
  com/
    davidpe/
      tasker/
        ui/
          main.fxml
          settings.fxml
          stats.fxml
          new-task.fxml
          new-project.fxml
          menu-task.fxml
          controls/
            chart-2d.fxml
            tasker-table-control.fxml
            tasker-row-control.fxml
            message-panel.fxml
          images/
            *.png
          fonts/
            *.ttf
          styles.css
```

Guidelines:
- `application.yml` holds Spring Boot configuration.
- `db/migration` follows Flyway naming: `V{number}__{description}.sql` (forward-only).
- `com/davidpe/tasker/ui` mirrors JavaFX screens defined in `UiScreenId`.
- `controls/` contains reusable UI component FXML files used by custom JavaFX controls.
- `images/` and `fonts/` are referenced from FXML and CSS using classpath URLs.
- Keep CSS under the same UI folder to simplify relative references (`stylesheets="@styles.css"`).

---

## 4) Spring Boot + JavaFX bootstrap

The app runs JavaFX and Spring in the same JVM. The JavaFX `Application` is responsible for launching Spring, then registering the JavaFX `Stage` in the Spring context.

```java
@SpringBootApplication
public class TaskerApplication extends Application {

  private ConfigurableApplicationContext applicationContext;

  @Override
  public void init() {
    SpringApplicationBuilder builder = new SpringApplicationBuilder(TaskerApplication.class);
    applicationContext = builder.headless(false).run();
  }

  @Override
  public void start(Stage primaryStage) {
    primaryStage.initStyle(StageStyle.TRANSPARENT);

    String appTitle = applicationContext.getBean("applicationTitle", String.class);
    primaryStage.setTitle(appTitle);

    applicationContext.getBeanFactory().registerSingleton("primaryStage", primaryStage);

    UiScreenFactory screenFactory = applicationContext.getBean(UiScreenFactory.class);
    screenFactory.create(UiScreenId.MAIN).show();
  }
}
```

Key points:
- `headless(false)` is required to allow JavaFX GUI.
- The `primaryStage` is registered as a Spring bean so other components can use it.
- The screen system is created after the stage is available.

`ApplicationConfig` exposes the `UiScreenFactory` as a lazy bean so JavaFX stage is ready first:

```java
@Configuration
public class ApplicationConfig {

  @Bean
  @Lazy
  public UiScreenFactory screenFactory(Stage stage) throws IOException {
    return new UiScreenFactory(stage, fxmlLoader);
  }
}
```

---

## 5) JavaFX window wrapper system

The app uses a wrapper abstraction so any part of the application can open or close screens without directly managing `Stage` or `Scene`.

### 5.1 UiScreen
`UiScreen` is the core interface for all screens:

```java
public sealed interface UiScreen permits AbstractUiScreen {
  UiScreenId id();
  Stage stage();
  Scene scene();
  void show();
  void hide();
  void reset();
  boolean isShowing();
  UiScreenController controller();
  void showAtPosition(java.awt.Point menuPosition);

  default <T> void setData(T data) {
    if (controller() instanceof UiControllerDataAware<?> dataAware) {
      UiControllerDataAware<T> typed = (UiControllerDataAware<T>) dataAware;
      typed.setData(data);
    }
  }
}
```

### 5.2 UiPrimaryScreen vs UiModalScreen
- `UiPrimaryScreen` replaces or controls the main stage.
- `UiModalScreen` is a separate modal stage used for dialogs and menus.

```java
public final class UiPrimaryScreen extends AbstractUiScreen {
  @Override
  public void show() {
    primaryStage.setScene(scene());
    primaryStage.show();
  }
}

public final class UiModalScreen extends AbstractUiScreen {
  @Override
  public void showAtPosition(java.awt.Point menuPosition) {
    Stage modalStage = ensureStage(menuPosition);
    modalStage.showAndWait();
  }
}
```

### 5.3 UiScreenFactory
The factory binds screen IDs to FXML and controllers, and caches screens for reuse.

```java
private static final Map<UiScreenId, UiScreenDescriptor<? extends UiScreenController>>
  SCREEN_DEFINITIONS = Map.of(
    UiScreenId.MAIN,
    new UiScreenDescriptor<>(
      UiScreenId.MAIN.getResourcePath(),
      (stage, supplier, controller) ->
        new UiPrimaryScreen(UiScreenId.MAIN, stage, supplier, controller),
      MainSceneController.class
    ),
    UiScreenId.NEW_TASK_DIALOG,
    new UiScreenDescriptor<>(
      UiScreenId.NEW_TASK_DIALOG.getResourcePath(),
      (stage, supplier, controller) ->
        new UiModalScreen(UiScreenId.NEW_TASK_DIALOG, stage, supplier, controller),
      NewTaskPanelController.class
    )
  );
```

The factory loads FXML using `UiViewLoader`:

```java
UiViewContext root = fxmlLoader.load(descriptor.fxml());
Supplier<Scene> supplier = () -> {
  Scene scene = new Scene(root.root());
  scene.setFill(Color.TRANSPARENT);
  return scene;
};
```

### 5.4 UiViewLoader
This is the bridge between Spring and JavaFX controllers. It ensures controllers are created by Spring.

```java
@Component
public class UiViewLoader {
  public UiViewContext load(String fxmlPath) throws IOException {
    URL resource = getClass().getResource(fxmlPath);
    FXMLLoader loader = new FXMLLoader(resource);
    loader.setControllerFactory(applicationContext::getBean);
    return new UiViewContext(loader.load(), loader.getController());
  }
}
```

### 5.5 UiFlowManager (navigation rules)
`UiFlowManager` centralizes rules for which screen to show or hide. Controllers publish events instead of navigating directly.

```java
@Component
@Lazy
public class UiFlowManager {

  @EventListener
  public void onWindowClosed(WindowClosedEvent ev) {
    Platform.runLater(() -> handleClosed(ev));
  }

  private void handleClosed(WindowClosedEvent ev) {
    if (ev.screenId() == UiScreenId.SETTINGS) {
      screenFactory.create(UiScreenId.MAIN).show();
    }
  }

  @EventListener
  private void onNewTaskOpened(WindowNewTaskOpenedEvent ev) {
    UiScreen dialog = screenFactory.create(UiScreenId.NEW_TASK_DIALOG);
    dialog.reset();
    dialog.setData(new NewTaskPanelData(NewTaskPanelData.OperationType.CREATE, null));
    dialog.show();
  }
}
```

Controllers only publish events, they do not manage navigation logic.

---

## 6) JavaFX windowing behavior (very important)

This is how the window system is designed and how to reproduce it.

### 6.1 Transparent windows
The primary stage uses a transparent style:

```java
primaryStage.initStyle(StageStyle.TRANSPARENT);
```

This allows custom window frames and visual effects defined in CSS.

### 6.2 Dragging the window
The main controller implements manual drag logic because the window has no native frame:

```java
private void moveMainWindowsSetUp() {
  mainPane.setOnMousePressed(event -> {
    xOffset = event.getSceneX();
    yOffset = event.getSceneY();
  });

  mainPane.setOnMouseDragged(event -> {
    Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
    stage.setX(event.getScreenX() - xOffset);
    stage.setY(event.getScreenY() - yOffset);
  });
}
```

### 6.3 Modal dialogs
Modal dialogs (New Task, New Project, Menu Task) are created by `UiModalScreen`:

- A new `Stage` is lazily created and cached.
- The modal stage is set to `StageStyle.TRANSPARENT`.
- It is `WINDOW_MODAL` and owned by the primary stage.
- It can be positioned using `showAtPosition(Point)`.

```java
cachedStage = new Stage();
cachedStage.initStyle(StageStyle.TRANSPARENT);
cachedStage.initModality(Modality.WINDOW_MODAL);
cachedStage.initOwner(primaryStage);
```

### 6.4 Auto-hide popup menu
The task context menu closes when focus is lost and when a click happens outside.

```java
private final EventHandler<MouseEvent> sceneClickHandler = event -> {
  Node target = event.getPickResult() != null ? event.getPickResult().getIntersectedNode() : null;
  if (target == null || !isDescendantOf(target, paneMenuTask)) {
    hideStage();
  }
};
```

This is why menu popups feel like native context menus without extra code in the main screen.

---

## 7) Controllers, views, and presenters

### 7.1 UiScreenController base class
All UI controllers extend this class for standard behavior (reset and stage access).

```java
public abstract class UiScreenController implements Initializable {
  protected Stage rootStage = null;
  public abstract void resetData();
  public void setRootStage(Stage stage) { this.rootStage = stage; }
  public void hideStage() { if (rootStage != null) rootStage.hide(); }
}
```

### 7.2 UiControllerDataAware
Controllers can receive data using `UiScreen.setData(...)`.

```java
public interface UiControllerDataAware<T> {
  void setData(T data);
  T getData();
}
```

Example usage:

```java
UiScreen dialog = screenFactory.create(UiScreenId.NEW_TASK_DIALOG);
dialog.setData(new NewTaskPanelData(OperationType.EDIT, taskId));
```

### 7.3 Presenter pattern for dialogs
Dialogs use presenters to keep UI logic thin and separate from business logic.

```java
@Component
public class NewTaskPresenter {
  public void onSaveRequested() {
    AddTaskCommand command = new AddTaskCommand(
      view.selectedProjectId(),
      view.selectedPriorityId(),
      view.selectedTagId(),
      view.externalCodeInput(),
      view.titleInput(),
      view.descriptionInput(),
      view.startDate(),
      view.endDate()
    );
    addTaskUseCase.addTask(command);
    view.close();
  }
}
```

---

## 8) Custom controls and reusable UI wrappers

The project uses custom JavaFX controls under `ui/controls` that load their own FXML files and provide a reusable API:

- `TaskerTablePanelController` (table view with paging and columns)
- `TaskerRowPanelController` (task row)
- `MessagePanelController` (confirm dialog)
- `Chart2DController` (custom chart control)

Example of a reusable control:

```java
public class TaskerRowPanelController extends Pane {
  public TaskerRowPanelController() {
    FXMLLoader fxmlLoader = new FXMLLoader(
      getClass().getResource("/com/davidpe/tasker/ui/controls/tasker-row-control.fxml")
    );
    fxmlLoader.setRoot(this);
    fxmlLoader.setController(this);
    fxmlLoader.load();
  }
}
```

This pattern is used to keep FXML for components modular.

### 8.1 Message panel inside a screen

`MessagePanelController` is a reusable control embedded inside a larger JavaFX screen. It is not navigated as a separate screen; the parent screen owns its lifecycle, visibility, and callbacks.

Project rules:

- The screen controller declares the panel as an `@FXML` field, for example `@FXML private MessagePanelController pnlMessage;`.
- The control loads its own FXML with `fx:root`, `setRoot(this)`, and `setController(this)`.
- The screen controller configures the panel during its own `initialize()` method.
- The screen sets the text with `setMessage(...)` and registers a `MessagePanelActionListener` with `setMessagePanelActionListener(...)`.
- Button events are handled inside the control with one `buttonAction(...)` method, but the screen decides what those actions mean.
- The usual response is to hide the panel with `setVisible(false)`.
- If the panel should appear with animation, the screen can fade it in and then show it.

Project example:

```java
@FXML private MessagePanelController pnlMessage;

private void initializeMessagePanel() {
  pnlMessage.setMessage("This is a note that you have to confirm, or not?");
  pnlMessage.setMessagePanelActionListener(
      new MessagePanelController.MessagePanelActionListener() {
        @Override
        public void onOkButtonClicked() {
          pnlMessage.setVisible(false);
        }

        @Override
        public void onCancelButtonClicked() {
          pnlMessage.setVisible(false);
        }
      });

  JavaFXAnimationUtil.animationBuilder()
      .duration(Duration.seconds(0.5))
      .onFinished(() -> pnlMessage.setVisible(true))
      .fadeIn(pnlMessage)
      .buildAndPlay();
}
```

FXML expectations for the control:

- Use `<fx:root>` as the root element.
- Bind children with `fx:id` fields defined in the controller.
- Wire both buttons to the same action handler, usually `onAction="#buttonAction"`.
- Keep the control self-contained so the screen only places it and decides when to show or hide it.

### 8.2 Reusable graphical button controls

When a JavaFX button is repeated across views, encapsulate it as a reusable visual control instead of duplicating a `Button`, `ImageView`, FXML handlers, and CSS in every screen.

Prefer the following composition for an icon button:

```text
GraphicButton
└── StackPane
    └── Button
        └── ImageView (graphic)
```

Keep the real `Button` as the interactive node. Use the `ImageView` only as its `graphic`. This preserves keyboard activation, focus behavior, accessibility semantics, disabled state, and normal JavaFX `ActionEvent` handling while allowing the control to look like a custom graphic button.

#### Control structure

For projects that already use FXML-backed controls, implement the control as a `StackPane` or `Pane` that loads its own FXML with `fx:root`:

```java
public final class GraphicButton extends StackPane {

    @FXML private Button button;
    @FXML private ImageView icon;

    public GraphicButton() {
        FXMLLoader loader = new FXMLLoader(
            getClass().getResource("/ui/controls/graphic-button.fxml"));
        loader.setRoot(this);
        loader.setController(this);
        try {
            loader.load();
        } catch (IOException exception) {
            throw new IllegalStateException(
                "Unable to load graphic-button.fxml", exception);
        }
    }

    public void setImage(Image image) {
        icon.setImage(image);
    }

    public void setOnAction(EventHandler<ActionEvent> handler) {
        button.setOnAction(handler);
    }

    public void setDisable(boolean disabled) {
        button.setDisable(disabled);
    }
}
```

The internal FXML should expose a `Button` with an `ImageView` in its `graphic` property. The public control API should expose configuration and events, not internal nodes. Typical properties are `image`, `imageUrl`, `onAction`, `tooltip`, `disable`, `selected`, `accessibleText`, and optional size or variant properties.

#### Event ownership

The reusable control should emit an action; the parent controller or application flow owns the business behavior:

```java
settingsButton.setOnAction(event -> flowManager.openSettings());
```

Do not put navigation, persistence, domain commands, or event-bus decisions inside a generic graphical button. If the project uses FXML handlers, expose a clear control-level event and keep the handler thin. `onAction` is generated by the `Button`, so the event source is normally the internal `Button`, not its `ImageView`.

#### CSS states

Give the outer control a stable style class and style the internal button through descendant selectors:

```css
.graphic-button .button {
    -fx-background-color: transparent;
    -fx-background-radius: 50%;
    -fx-border-color: -fx-control-inner-background;
    -fx-border-width: 1;
    -fx-border-radius: 50%;
    -fx-padding: 6;
    -fx-cursor: hand;
}

.graphic-button .button:hover {
    -fx-background-color: -fx-hover-base;
    -fx-border-color: white;
}

.graphic-button .button:pressed {
    -fx-background-color: -fx-pressed-base;
}

.graphic-button .button:focused {
    -fx-border-color: -fx-focus-color;
}

.graphic-button .button:disabled {
    -fx-opacity: 0.4;
    -fx-cursor: default;
}

.graphic-button .image-view {
    -fx-opacity: 0.8;
}

.graphic-button:hover .image-view {
    -fx-opacity: 1.0;
}
```

Use JavaFX pseudo-classes (`:hover`, `:pressed`, `:focused`, `:disabled`) for transient interaction states. If the control has a persistent selection state, expose `selected` and synchronize it with a custom pseudo-class:

```java
private static final PseudoClass SELECTED =
    PseudoClass.getPseudoClass("selected");

public void setSelected(boolean value) {
    pseudoClassStateChanged(SELECTED, value);
}
```

```css
.graphic-button:selected .button {
    -fx-background-color: -fx-selected-base;
    -fx-border-color: -fx-accent;
}
```

Do not use CSS animation properties that JavaFX does not support as if they were web CSS (for example, `-fx-transition`). Implement animation explicitly with `Timeline`, `ScaleTransition`, `FadeTransition`, or another JavaFX animation when needed.

#### Integration and variants

Declare the control as a normal custom component in any view:

```xml
<GraphicButton fx:id="closeButton" />
```

Configure its icon and action in the owning controller. Reuse one base control for icon-only actions such as close, back, settings, minimize, or maximize. Create a separate `MenuOption` or `SelectableCard` control when the visual unit also owns text, descriptions, grouping, or a persistent selection state; do not overload the icon-button API with unrelated layout responsibilities.

For simple one-off controls, a plain `Button` with a `graphic` and a shared style class is sufficient. Use a custom control when the composition, public API, behavior, or styling is repeated in at least two views or requires consistent lifecycle and accessibility behavior.

#### Verification checklist

- Verify that the control is activatable with mouse, keyboard, and assistive technologies.
- Verify `hover`, `pressed`, `focused`, `disabled`, and, if applicable, `selected` states.
- Verify that the icon preserves its aspect ratio and has a useful accessible description.
- Verify that the parent receives the action without depending on the internal `Button` or `ImageView`.
- Verify that FXML loading works both standalone and when nested in another view.
- Test the control in light/dark themes or the project's supported style variants.

---

## 9) Window flow examples

### 9.1 Create new task
1. User clicks New Task.
2. Controller publishes `WindowNewTaskOpenedEvent`.
3. `UiFlowManager` reacts and opens the dialog.

```java
// MainSceneController
if (isButtonNewTaskClicked(event)) {
  eventPublisher.publishEvent(new WindowNewTaskOpenedEvent());
}

// UiFlowManager
@EventListener
private void onNewTaskOpened(WindowNewTaskOpenedEvent ev) {
  UiScreen dialog = screenFactory.create(UiScreenId.NEW_TASK_DIALOG);
  dialog.reset();
  dialog.setData(new NewTaskPanelData(OperationType.CREATE, null));
  dialog.show();
}
```

### 9.2 Edit task from context menu
1. User right clicks a task row.
2. A `WindowMenuTaskOpenedEvent` is fired with the menu position.
3. `UiFlowManager` opens the menu at the given screen position.
4. User selects action in menu, publishes `WindowMenuTaskSelectedEvent`.
5. Main controller reacts and triggers edit.

```java
// UiFlowManager
@EventListener
private void onMenuTaskOpened(WindowMenuTaskOpenedEvent ev) {
  UiScreen menu = screenFactory.create(UiScreenId.MENU_TASK_DIALOG);
  menu.reset();
  menu.setData(new MenuTaskData(ev.getTaskId()));
  menu.showAtPosition(ev.getMenuPosition());
}
```

---

## 10) FXML conventions and fx:id naming

The project uses consistent `fx:id` naming conventions to keep controller fields readable and easy to scan:

- Button: `btnSave`, `btnClose`
- Label: `lblTitle`, `lblName`
- TextField: `txtTitle`
- ComboBox: `cbxProject`
- Pane: `paneMenuTask`

FXML example:

```xml
<Button fx:id="btnNewTask" onAction="#buttonAction" />
<ComboBox fx:id="cbxProject" onAction="#onProjectChanged" />
```

---

## 11) Adding a new screen (pattern)

To add a new screen that follows the same design:

1) Create an FXML file under `src/main/resources/com/davidpe/tasker/ui/`.
2) Create a controller that extends `UiScreenController`.
3) If it needs data, implement `UiControllerDataAware<T>`.
4) Register the screen in `UiScreenId`.
5) Register it in `UiScreenFactory` with the proper screen type.
6) Publish and react to window events (optional).

Example:

```java
// UiScreenId
NEW_REPORT_DIALOG("/com/davidpe/tasker/ui/new-report.fxml"),

// UiScreenFactory
UiScreenId.NEW_REPORT_DIALOG,
new UiScreenDescriptor<>(
  UiScreenId.NEW_REPORT_DIALOG.getResourcePath(),
  (stage, supplier, controller) ->
    new UiModalScreen(UiScreenId.NEW_REPORT_DIALOG, stage, supplier, controller),
  NewReportPanelController.class
);
```

---

## 12) Summary of the design rules

- Keep domain clean: no Spring, no JavaFX.
- Use application use cases as the entry point for business logic.
- Keep UI controllers thin; put logic into presenters.
- Navigate by publishing window events, not by directly calling `show()`.
- Centralize all navigation logic in `UiFlowManager`.
- Use `UiScreenFactory` to abstract screen creation and caching.
- Use modal screens for dialogs and menus, and `UiPrimaryScreen` for full scenes.
- Use transparent stages and custom window drag logic for a modern UI.

This is the core design system behind Tasker. Replicating these decisions yields a scalable JavaFX desktop app with clean architecture and maintainable UI flow.

---

## 13) FXML example (simple modal)

This is a compact FXML example for a modal dialog that follows the same `fx:id` conventions and controller binding style used in Tasker:

```xml
<?xml version="1.0" encoding="UTF-8"?>

<?import javafx.scene.control.Button?>
<?import javafx.scene.control.Label?>
<?import javafx.scene.control.TextField?>
<?import javafx.scene.layout.Pane?>
<?import javafx.scene.text.Font?>

<Pane prefHeight="260.0" prefWidth="520.0"
      styleClass="note-panel-shadow-strong"
      stylesheets="@styles.css"
      xmlns:fx="http://javafx.com/fxml/1"
      fx:controller="com.example.app.ui.project.NewProjectPanelController">

  <children>
    <Label fx:id="lblTitle" layoutX="24.0" layoutY="18.0"
      styleClass="modal-title-text" text="New Project" />

    <Label fx:id="lblSubtitle" layoutX="24.0" layoutY="50.0"
      styleClass="button-ok" text="Current user" />

    <Label fx:id="lblName" layoutX="24.0" layoutY="100.0"
      styleClass="note-label" text="Title" />

    <TextField fx:id="txtTitle" layoutX="24.0" layoutY="124.0"
     prefHeight="32.0" prefWidth="470.0"
     promptText="Project name" styleClass="popup-text-field" />

    <Label fx:id="lblError" layoutX="24.0" layoutY="164.0"
      prefWidth="470.0" textFill="red" />

    <Button fx:id="btnCancel" layoutX="290.0" layoutY="200.0"
       prefHeight="32.0" prefWidth="98.0"
       styleClass="button-cancel" text="Cancel" onAction="#buttonAction">
      <font><Font name="Alatsi Regular" size="18.0" /></font>
    </Button>

    <Button fx:id="btnOk" layoutX="396.0" layoutY="200.0"
       prefHeight="32.0" prefWidth="98.0"
       styleClass="button-ok" text="Save" onAction="#buttonAction">
      <font><Font name="Alatsi Regular" size="18.0" /></font>
    </Button>
  </children>
</Pane>
```
