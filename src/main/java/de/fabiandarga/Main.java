package de.fabiandarga;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.sql.*;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

public class Main {
    public static void main(String[] args) throws IOException, SQLException {
        String url = "jdbc:postgresql://localhost:5432/numbers_and_colors";
        String user = "numbers_and_colors_admin";
        String password = "123456";

        Connection conn = DriverManager.getConnection(url, user, password);

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/", new MyHandler(conn));

        // Start the server
        server.start();
        System.out.println("Server started on port 8080");
    }

    static class MyHandler implements HttpHandler {
        private final Connection conn;
        private HashMap<Integer, String> cache = new HashMap();

        public MyHandler(Connection conn) {
            this.conn = conn;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            long tsStart = System.currentTimeMillis();

            // Parse query parameters
            Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI().getQuery());

            // Get the "number" parameter from the query string
            String numberParam = queryParams.get("number");
            String response;

            if (numberParam != null) {
                try {
                    int number = Integer.parseInt(numberParam);
                    response = "You entered the number: " + number;

                    if (this.cache.containsKey(number)) {
                        response +=  this.cache.get(number);
                        System.out.println("reading from cache for number " + number);
                    } else {
                        // Do the Database query
                        String sql = "SELECT color, COUNT(*) as amount FROM pairs WHERE number=? GROUP BY color";
                        PreparedStatement statement = this.conn.prepareStatement(sql);
                        statement.setInt(1, number);
                        ResultSet rs = statement.executeQuery();

                        ArrayList<String> colors = new ArrayList<>();
                        while (rs.next()) {
                            String group = rs.getString("color") + ": " + rs.getInt("amount");
                            colors.add(group);
                        }
                        response += colors;
                        this.cache.put(number, colors.toString());
                        System.out.println("wrote to cache for number " + number);
                    }
                } catch (NumberFormatException e) {
                    response = "Invalid number format.";
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            } else {
                response = "Please provide a 'number' parameter.";
            }


            long tsDiff = System.currentTimeMillis() - tsStart;
            response += " ( duration=" + tsDiff + " ms )";
            // Send response back to client
            exchange.sendResponseHeaders(200, response.length()); // HTTP 200 OK
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }



        // Utility method to parse query parameters
        private Map<String, String> parseQueryParams(String query) {
            Map<String, String> queryParams = new HashMap<>();
            if (query != null) {
                String[] pairs = query.split("&");
                for (String pair : pairs) {
                    String[] keyValue = pair.split("=");
                    if (keyValue.length > 1) {
                        queryParams.put(keyValue[0], keyValue[1]);
                    }
                }
            }
            return queryParams;
        }
    }
}