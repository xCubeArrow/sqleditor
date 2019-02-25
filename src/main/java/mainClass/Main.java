package mainClass;

import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.util.Callback;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

public class Main extends Application {
    private Connection connection;
    private Stage stage;

    public static void main(String[] args) {
        Application.launch();
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        //Set the password new if it wasn't yet specified
        if (Files.readAllLines(Paths.get("res/password.txt")).size() == 0)
            setPassword();

        //Connect to the database using the password
        connectDatabase();

        this.stage = primaryStage;

        //Generate the tabPane to be used in the stage
        TabPane tabPane = updateUI("", "");

        //Set the Scene and display it in a window
        primaryStage.setScene(new Scene(tabPane));
        primaryStage.show();
    }

    private TabPane updateUI(String selectedDB, String selectedTable) {
        //Get the databases used in the server
        String[] databases = getDatabases();

        //return the tabPane that is generated
        return getTabPane(databases, selectedDB, selectedTable);
    }

    private TabPane getTabPane(String[] databases, String selected, String selectedTable) {
        TabPane tabPane = new TabPane();

        //Iterate over the database names
        for (String databaseName : databases) {

            //Find out if the database is not intended to be managed by searching for the names
            // that are preselected
            if ((!databaseName.equals("#mysql50#IB files")) && (!databaseName.equals("mysql")) && (!databaseName.equals("sys"))
                    && (!databaseName.equals("information_schema")) && (!databaseName.equals("performance_schema"))) {
                //Create tab with the database's name
                Tab tab = new Tab(databaseName);
                //Set the OnClose Request listener to call deleteDB()
                tab.setOnCloseRequest(event -> deleteDB(databaseName));
                //Set the content of the tab to be a tabPane for the database
                tab.setContent(getTabPaneForDatabase(databaseName, selectedTable));
                //Create the tabs
                addTabAndSelectIt(selected, tabPane, databaseName, tab);
            }
        }
        //Add a button to create a new database
        Button addDBButton = new Button("+");
        addDBButton.setOnAction(event -> addDataBase());

        //Create a tab for the add button
        configureAddButtonAsTab(tabPane, addDBButton);

        return tabPane;
    }

    private void configureAddButtonAsTab(TabPane tabPane, Button addDBButton) {
        //Create a tab and set some parameters
        Tab tab = new Tab();
        tab.setClosable(false);
        tab.setGraphic(addDBButton);
        //Add the tab to the tabPane
        tabPane.getTabs().add(tab);
    }

    private void deleteDB(String databaseName) {
        //Ask the user if he really wants to delete the database
        if (getConfirmation("Delete Database", "Do you really want to delete the Database named " + databaseName + "?")) {
            try {
                //Delete the database
                connection.prepareStatement("DROP DATABASE " + databaseName + ";").executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        //Update the stage
        stage.setScene(new Scene(updateUI(databaseName, "")));
    }

    private void addDataBase() {
        //Ask the user for the name of the new Database
        String new_database = getTextInput("New Database", "What should the new Database be named?");

        try {
            //Create a new database with the name and update the stage
            connection.prepareStatement("CREATE DATABASE " + new_database + ";").executeUpdate();
            stage.setScene(new Scene(updateUI(new_database, "")));

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private TabPane getTabPaneForDatabase(String databaseName, String selectedTable) {
        TabPane tabPane = new TabPane();

        //Get the tables in the database
        String[] tables = getTables(databaseName);

        //Iterate over the table'S names
        for (String tableName : tables) {
            //Create the tab for the table
            Tab tab = new Tab(tableName);
            //Delete the table on close handle
            tab.setOnCloseRequest(event -> deleteTable(tableName, databaseName));
            //Set the content for the tab
            tab.setContent(tableAndButtons(tableName, databaseName));

            addTabAndSelectIt(selectedTable, tabPane, tableName, tab);
        }
        //Create the Button to add another table and add it to a tab
        Button addTableBtn = new Button("+");
        addTableBtn.setOnAction(event -> addTable(databaseName));
        configureAddButtonAsTab(tabPane, addTableBtn);

        return tabPane;
    }

    private void addTabAndSelectIt(String selectedTable, TabPane tabPane, String tableName, Tab tab) {
        //Add the tab to the tabPane
        tabPane.getTabs().add(tab);
        //Select the tab
        if (!selectedTable.equals("") && selectedTable.equals(tableName)) {
            SingleSelectionModel<Tab> selectionModel = tabPane.getSelectionModel();
            selectionModel.select(tab);
        }
    }

    private void deleteTable(String tableName, String databaseName) {
        //Ask the user if he really wants to delete the table
        if (getConfirmation("Delete Table", "Do you really want to delete the table named " + tableName + "?")) {
            try {
                //Drop the table
                connection.prepareStatement("DROP TABLE " + tableName + ";").executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        //Update the stage
        stage.setScene(new Scene(updateUI(databaseName, tableName)));
    }

    private void addTable(String databaseName) {
        try {
            //Ask the user for all of the information needed to create the table including all column names and types
            String name = getTextInput("New Table", "What should tables name be?").toLowerCase();
            int columnCount = Integer.parseInt(getTextInput("Columns", "How many columns should the Table have?"));
            String[] columnNames = new String[columnCount];
            for (int i = 0; i < columnCount; i++) {
                String columnName = getTextInput("Column", "What should the " + (i + 1) + ". column be called?").toLowerCase();
                String type = getTextInput("Column Type", "What should the data type be?").toUpperCase();
                columnNames[i] = columnName + " " + type;
            }

            //Create the table using the data
            String queryColumnNames = String.join(", ", columnNames);
            connection.prepareStatement(String.format("CREATE TABLE %s.%s (%s);", databaseName, name, queryColumnNames)).executeUpdate();

            //Update the stage
            stage.setScene(new Scene(updateUI(databaseName, name)));
        } catch (Exception e1) {
            e1.printStackTrace();
        }
    }

    private BorderPane tableAndButtons(String tableName, String database) {
        //Create the needed variables
        TableView tableView = new TableView();
        BorderPane pane = new BorderPane();
        HBox actionPane = new HBox();
        try {
            //Add all the colums of the table to the tableView
            String[] columns = addColumnsToTableView(tableName, database, tableView);

            //Add the data to the tableView
            addDataToTableView(tableName, database, tableView, columns);

            //Configure a button that removes a row from the table
            Button removeButton = new Button("Remove Row");
            removeButton.setOnAction(event -> {
                //Ask the user which id is to be removed
                String id = getTextInput("id", "Please enter the ID: ");
                try {
                    //Remove from the table where the id is right
                    PreparedStatement statement = connection.prepareStatement("" +
                            "DELETE FROM " + database + "." + tableName + " WHERE id=?");
                    statement.setString(1, id);
                    statement.executeUpdate();
                    stage.setScene(new Scene(updateUI(database, tableName)));
                } catch (SQLException e) {
                    e.printStackTrace();
                }

            });
            Button addButton = new Button("Add Row");
            addButton.setOnAction(event -> {
                try {
                    ArrayList<Object> arrayList = new ArrayList<>();
                    for (int i = 1; i < columns.length; i++) {
                        arrayList.add(getTextInput("Parameter", "What should the " + columns[i] + " parameter be?"));
                    }
                    PreparedStatement statement = connection.prepareStatement("INSERT INTO " + database + "." + tableName + " VALUES (DEFAULT, " + getQuestionMarks(columns.length - 1) + ");");

                    for (int i = 0; i < arrayList.size(); i++) {
                        statement.setObject(i + 1, arrayList.get(i));
                    }
                    statement.executeUpdate();
                    stage.setScene(new Scene(updateUI(database, tableName)));
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            });
            Button updateRow = new Button("Update Row");
            updateRow.setOnAction(event -> {
                updateRow(getTextInput("ID", "Please enter the id: "), database, tableName, columns);
            });

            Button setPassword = new Button("Set Password");
            setPassword.setOnAction(event -> setPassword());

            actionPane.getChildren().addAll(addButton, removeButton, updateRow, setPassword);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        pane.setCenter(tableView);
        pane.setBottom(actionPane);
        return pane;
    }

    private void addDataToTableView(String tableName, String database, TableView tableView, String[] columns) throws SQLException {
        PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + database + "." + tableName + ";");
        ResultSet resultSet = statement.executeQuery();

        while (resultSet.next()) {
            ObservableList<StringProperty> data = FXCollections.observableArrayList();
            for (int i = 1; i <= columns.length; i++) {
                data.add(new SimpleStringProperty(String.valueOf(resultSet.getObject(i))));
            }
            tableView.getItems().add(data);
        }
    }

    private String[] addColumnsToTableView(String tableName, String database, TableView tableView) {
        String[] columns = getColumns(tableName, database);
        for (int i = 0; i < columns.length; i++) {

            TableColumn tableColumn = new TableColumn();
            tableColumn.setText(columns[i]);
            int finalI = i;
            tableColumn.setCellValueFactory((Callback<TableColumn.CellDataFeatures<ObservableList<StringProperty>, String>, ObservableValue<String>>) cellDataFeatures -> {
                ObservableList<StringProperty> values = cellDataFeatures.getValue();
                if (finalI >= values.size()) {
                    return new SimpleStringProperty("");
                } else {
                    return cellDataFeatures.getValue().get(finalI);
                }
            });
            tableView.getColumns().add(tableColumn);
        }
        return columns;
    }

    private String[] getTables(String databaseName) {
        try {
            PreparedStatement statement = connection.prepareStatement("SHOW TABLES FROM " + databaseName + ";");
            return getFirstStringsFromResultSet(statement);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return new String[0];
    }

    private String[] getColumns(String tableName, String databaseName) {
        try {
            PreparedStatement statement = connection.prepareStatement("SHOW COLUMNS FROM " + databaseName + "." + tableName + ";");
            return getFirstStringsFromResultSet(statement);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return new String[0];
    }

    private String[] getFirstStringsFromResultSet(PreparedStatement statement) throws SQLException {
        ResultSet resultSet = statement.executeQuery();
        ArrayList<String> tables = new ArrayList<>();
        while (resultSet.next()) {
            tables.add(resultSet.getString(1));
        }
        return tables.toArray(new String[0]);
    }

    private void updateRow(String id, String database, String tableName, String[] columns) {
        String[] newValues = new String[columns.length - 1];
        for (int i = 1; i < columns.length; i++) {
            newValues[i - 1] = columns[i] + "='" + getTextInput("New Value", "Please enter the new value for " + columns[i] + ": ") + "'";
        }
        String argumentQuery = "SET " + Arrays.stream(newValues).collect(Collectors.joining(", "));
        try {
            PreparedStatement statement = connection.prepareStatement("UPDATE " + database + "." + tableName + " " + argumentQuery + "WHERE id='" + id + "';");
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        stage.setScene(new Scene(updateUI(database, tableName)));

    }

    private String[] getDatabases() {
        try {
            PreparedStatement statement = connection.prepareStatement("SHOW DATABASES");
            return getFirstStringsFromResultSet(statement);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return new String[0];
    }

    private void connectDatabase() {
        try {
            String[] info = Files.readAllLines(Paths.get("res/password.txt")).toArray(new String[0]);
            connection = DriverManager.getConnection(String.format("jdbc:mysql://%s:%s", info[0], info[1]), info[2], info[3]);
        } catch (IOException | SQLException e) {
            e.printStackTrace();
        }
    }

    private void setPassword() {
        String host = getTextInput("Host", "Please enter your Host: ");
        String port = getTextInput("Port", "Please enter your Port: ");
        String username = getTextInput("Username", "Please enter your Username: ");
        String password = getTextInput("Password", "Please enter your Password: ");

        try {
            FileWriter writer = new FileWriter(new File("res/password.txt"));
            writer.write(String.format("%s\n%s\n%s\n%s", host, port, username, password));
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getQuestionMarks(int amount) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < amount; i++) {
            s.append("?, ");
        }
        return s.substring(0, s.length() - 2);
    }

    private String getTextInput(String title, String contentText) {
        TextInputDialog inputDialog = new TextInputDialog();
        inputDialog.setTitle(title);
        inputDialog.setHeaderText(null);
        inputDialog.setContentText(contentText);
        inputDialog.setResizable(true);
        Optional<String> result = inputDialog.showAndWait();
        return result.orElse("");
    }

    private boolean getConfirmation(String title, String contentText) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        alert.setContentText(contentText);

        Optional<ButtonType> result = alert.showAndWait();
        return result.filter(buttonType -> buttonType == ButtonType.OK).isPresent();
    }
}
