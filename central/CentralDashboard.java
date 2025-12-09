package p2.central;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.*;
import java.util.*;
import java.util.Date;
import java.util.List;
import javax.swing.Timer;
import p2.db.DBManager;

@SuppressWarnings("serial")
public class CentralDashboard extends JFrame {
    private static CentralDashboard instancia = null;

    private Map<String, CPInfo> cpMap;
    private List<DriverRequest> driverRequests;
    private JPanel mainPanel;
    private JPanel cpGridPanel;
    private JPanel requestsPanel;
    private JPanel messagesPanel;
    private Timer refreshTimer;
    private JTextArea messagesArea;

    public CentralDashboard() {
        cpMap = new HashMap<>();
        driverRequests = new ArrayList<>();

        instancia = this;

        DBManager.connect();

        setupUI();
        startAutoRefresh();

        cargarDatosIniciales();
    }

    public static void refrescarDesdeCentral() {
        if (instancia != null) {
            SwingUtilities.invokeLater(() -> instancia.refreshData());
        }
    }

    private void cargarDatosIniciales() {
        cargarCPsDesdeBD();
        cargarPeticionesDesdeBD();
        cargarMensajesDesdeBD();
    }

    private void cargarCPsDesdeBD() {
        cpMap.clear();

        String sql = "SELECT id, ubicacion, estado, funciona, registrado_central, " +
                "conductor_actual, consumo_actual, importe_actual, precio_kwh " +
                "FROM charging_point";

        try (Connection conn = DBManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String id = rs.getString("id");
                String ubicacion = rs.getString("ubicacion");
                String estadoBD = rs.getString("estado");
                boolean funciona = rs.getBoolean("funciona");
                boolean registrado = rs.getBoolean("registrado_central");
                String conductorActual = rs.getString("conductor_actual");
                double consumoActual = rs.getDouble("consumo_actual");
                double importeActual = rs.getDouble("importe_actual");
                double precioKwh = rs.getDouble("precio_kwh");

                String estadoVisual = estadoBD;
                String color = "GRIS";

                if (registrado) {
                    if (estadoBD == null) estadoBD = "DESCONECTADO";
                    switch (estadoBD) {
                        case "ACTIVADO":
                            estadoVisual = "Activado";
                            color = "VERDE";
                            break;
                        case "PARADO":
                            estadoVisual = "Parado";
                            color = "NARANJA";
                            break;
                        case "SUMINISTRANDO":
                            estadoVisual = "Suministrando";
                            color = "VERDE";
                            break;
                        case "AVERIADO":
                        case "AVER IADO": // por si hay errores tipográficos
                            estadoVisual = "Averiado";
                            color = "ROJO";
                            break;
                        default:
                            estadoVisual = "Desconectado";
                            color = "GRIS";
                    }
                } else {
                    estadoVisual = "Desconectado";
                    color = "GRIS";
                }

                String consumoStr;
                if ("Suministrando".equals(estadoVisual)) {
                    consumoStr = String.format("%.2f kW", consumoActual); 
                } else {
                    consumoStr = String.format("%.3f €/kWh", precioKwh);
                }

                String driverId = null;
                String driverConsumo = null;
                String driverCosto = null;

                if ("Suministrando".equals(estadoVisual) && conductorActual != null) {
                    driverId = conductorActual;
                    driverConsumo = String.format("%.1f kWh", consumoActual);
                    driverCosto = String.format("%.2f €", importeActual);
                }

                cpMap.put(id, new CPInfo(id, ubicacion, consumoStr, estadoVisual, color,
                        driverId, driverConsumo, driverCosto));
            }

        } catch (SQLException e) {
            System.err.println("Error cargando CPs desde BD: " + e.getMessage());
            agregarMensaje("ERROR: No se pudieron cargar los CPs desde la BD");
        }
    }

    private void cargarPeticionesDesdeBD() {
        driverRequests.clear();

        String sql = "SELECT cs.session_id, cs.conductor_id, cs.cp_id, cs.inicio, cs.estado " +
                "FROM charging_session cs " +
                "WHERE cs.estado IN ('EN_CURSO','EN_PROGRESO','EN_PROCESO') " +
                "ORDER BY cs.inicio DESC";

        try (Connection conn = DBManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String sessionId = rs.getString("session_id");
                String conductorId = rs.getString("conductor_id");
                String cpId = rs.getString("cp_id");
                Timestamp inicio = rs.getTimestamp("inicio");
                String estado = rs.getString("estado");

                String fecha = "";
                String hora = "";
                if (inicio != null) {
                    fecha = String.format("%td/%<tm/%<tY", inicio);
                    hora = String.format("%tH:%<tM", inicio);
                }

                driverRequests.add(new DriverRequest(fecha, hora, conductorId, cpId));
            }

        } catch (SQLException e) {
            System.err.println("Error cargando peticiones desde BD: " + e.getMessage());
        }
    }

    private void cargarMensajesDesdeBD() {
        StringBuilder mensajes = new StringBuilder();

        String sql = "SELECT cp_id, tipo_evento, descripcion, fecha " +
                "FROM event_log " +
                "ORDER BY fecha DESC " +
                "LIMIT 10";

        try (Connection conn = DBManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String cpId = rs.getString("cp_id");
                String tipoEvento = rs.getString("tipo_evento");
                String descripcion = rs.getString("descripcion");
                Timestamp fecha = rs.getTimestamp("fecha");

                String hora = "";
                if (fecha != null) {
                    hora = String.format("%tH:%<tM:%<tS", fecha);
                }
                String mensaje = String.format("[%s] %s: %s - %s\n",
                        hora, cpId != null ? cpId : "SISTEMA", tipoEvento, descripcion);

                mensajes.append(mensaje);
            }

            if (messagesArea != null) {
                messagesArea.setText(mensajes.toString());
            }

        } catch (SQLException e) {
            System.err.println("Error cargando mensajes desde BD: " + e.getMessage());
            agregarMensaje("ERROR: No se pudieron cargar los mensajes desde la BD");
        }
    }

    private void agregarMensaje(String mensaje) {
        if (messagesArea != null) {
            String timestamp = String.format("[%tH:%<tM:%<tS]", new Date());
            messagesArea.append(timestamp + " " + mensaje + "\n");

            // Auto-scroll to bottom
            messagesArea.setCaretPosition(messagesArea.getDocument().getLength());
        }
    }

    private void setupUI() {
        setTitle("EV Charging Central - Monitorization Panel (BD Real)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 800);
        setLocationRelativeTo(null);

        mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        mainPanel.setBackground(Color.DARK_GRAY);

        JLabel titleLabel = new JLabel("*** EV CHARGING SOLUTION: MONITORIZATION PANEL (BASE DE DATOS) ***", JLabel.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 20, 0));
        mainPanel.add(titleLabel, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new GridLayout(1, 2, 20, 0));
        centerPanel.setBackground(Color.DARK_GRAY);

        cpGridPanel = createCPGridPanel();
        centerPanel.add(cpGridPanel);

        JPanel rightPanel = new JPanel(new BorderLayout(0, 20));
        rightPanel.setBackground(Color.DARK_GRAY);

        requestsPanel = createRequestsPanel();
        messagesPanel = createMessagesPanel();

        rightPanel.add(requestsPanel, BorderLayout.CENTER);
        rightPanel.add(messagesPanel, BorderLayout.SOUTH);

        centerPanel.add(rightPanel);
        mainPanel.add(centerPanel, BorderLayout.CENTER);

        JPanel statsPanel = createStatsPanel();
        mainPanel.add(statsPanel, BorderLayout.SOUTH);

        add(mainPanel);
    }

    private JPanel createCPGridPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.DARK_GRAY);

        JLabel title = new JLabel("CHARGING POINTS STATUS (Desde BD)", JLabel.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 18));
        title.setForeground(Color.WHITE);
        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        panel.add(title, BorderLayout.NORTH);

        JPanel gridContainer = new JPanel(new BorderLayout());
        gridContainer.setBackground(Color.DARK_GRAY);

        JPanel gridPanel = new JPanel(new GridLayout(0, 3, 10, 10));
        gridPanel.setBackground(Color.DARK_GRAY);
        gridContainer.add(gridPanel, BorderLayout.CENTER);

        panel.add(gridContainer, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createCPCard(CPInfo cp) {
        JPanel card = new JPanel(new BorderLayout(5, 5));
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(getStatusColor(cp.statusColor), 2),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        card.setBackground(Color.WHITE);
        card.setPreferredSize(new Dimension(300, 200));

        JLabel idLabel = new JLabel(cp.id, JLabel.CENTER);
        idLabel.setFont(new Font("Arial", Font.BOLD, 16));
        idLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
        card.add(idLabel, BorderLayout.NORTH);

        JPanel infoPanel = new JPanel(new GridLayout(0, 1, 2, 2));
        infoPanel.setBackground(Color.WHITE);

        JLabel locationLabel = new JLabel(cp.location, JLabel.CENTER);
        locationLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        infoPanel.add(locationLabel);

        JLabel consumptionLabel = new JLabel(cp.consumption, JLabel.CENTER);
        consumptionLabel.setFont(new Font("Arial", Font.BOLD, 14));
        consumptionLabel.setForeground(Color.BLUE);
        infoPanel.add(consumptionLabel);

        if (cp.driverId != null && !cp.driverId.isEmpty()) {
            JLabel driverLabel = new JLabel(cp.driverId, JLabel.CENTER);
            driverLabel.setFont(new Font("Arial", Font.BOLD, 12));
            driverLabel.setForeground(Color.RED);
            infoPanel.add(driverLabel);

            if (cp.driverConsumption != null) {
                JLabel driverConsumption = new JLabel(cp.driverConsumption, JLabel.CENTER);
                driverConsumption.setFont(new Font("Arial", Font.PLAIN, 11));
                infoPanel.add(driverConsumption);
            }

            if (cp.driverCost != null) {
                JLabel driverCost = new JLabel(cp.driverCost, JLabel.CENTER);
                driverCost.setFont(new Font("Arial", Font.PLAIN, 11));
                driverCost.setForeground(Color.GREEN.darker());
                infoPanel.add(driverCost);
            }
        }

        card.add(infoPanel, BorderLayout.CENTER);

        JLabel statusLabel = new JLabel(cp.status, JLabel.CENTER);
        statusLabel.setFont(new Font("Arial", Font.ITALIC, 10));
        statusLabel.setForeground(getStatusColor(cp.statusColor));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
        card.add(statusLabel, BorderLayout.SOUTH);

        return card;
    }

    private Color getStatusColor(String status) {
        switch (status.toUpperCase()) {
            case "VERDE": return Color.GREEN;
            case "NARANJA": return Color.ORANGE;
            case "ROJO": return Color.RED;
            case "GRIS": return Color.GRAY;
            default: return Color.BLACK;
        }
    }

    private JPanel createRequestsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.DARK_GRAY);
        panel.setBorder(BorderFactory.createCompoundBorder(
                new TitledBorder(new LineBorder(Color.YELLOW, 2),
                        "*** PETICIONES ACTIVAS DE DRIVERS ***",
                        TitledBorder.CENTER, TitledBorder.TOP,
                        new Font("Arial", Font.BOLD, 14), Color.YELLOW),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBackground(Color.DARK_GRAY);
        panel.add(tablePanel, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createMessagesPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.DARK_GRAY);
        panel.setBorder(BorderFactory.createCompoundBorder(
                new TitledBorder(new LineBorder(Color.CYAN, 2),
                        "*** MENSAJES DEL SISTEMA ***",
                        TitledBorder.CENTER, TitledBorder.TOP,
                        new Font("Arial", Font.BOLD, 14), Color.CYAN),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        messagesArea = new JTextArea(6, 30);
        messagesArea.setFont(new Font("Arial", Font.PLAIN, 12));
        messagesArea.setBackground(Color.BLACK);
        messagesArea.setForeground(Color.WHITE);
        messagesArea.setEditable(false);
        messagesArea.setText("Cargando mensajes desde la base de datos...\n");

        JScrollPane scrollPane = new JScrollPane(messagesArea);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createStatsPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 5));
        panel.setBackground(Color.DARK_GRAY);
        panel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(Color.WHITE, 1),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));

        JLabel totalLabel = new JLabel("Total CPs: 0");
        JLabel activosLabel = new JLabel("Activos: 0");
        JLabel suministrandoLabel = new JLabel("Suministrando: 0");
        JLabel averiadosLabel = new JLabel("Averiados: 0");

        Color labelColor = Color.YELLOW;
        totalLabel.setForeground(labelColor);
        activosLabel.setForeground(labelColor);
        suministrandoLabel.setForeground(labelColor);
        averiadosLabel.setForeground(labelColor);

        totalLabel.setFont(new Font("Arial", Font.BOLD, 12));
        activosLabel.setFont(new Font("Arial", Font.BOLD, 12));
        suministrandoLabel.setFont(new Font("Arial", Font.BOLD, 12));
        averiadosLabel.setFont(new Font("Arial", Font.BOLD, 12));

        panel.add(totalLabel);
        panel.add(activosLabel);
        panel.add(suministrandoLabel);
        panel.add(averiadosLabel);

        return panel;
    }

    private void startAutoRefresh() {
        refreshTimer = new Timer(3000, new ActionListener() { 
            @Override
            public void actionPerformed(ActionEvent e) {
                refreshData();
            }
        });
        refreshTimer.start();
    }

    private void refreshData() {
        cargarCPsDesdeBD();
        cargarPeticionesDesdeBD();
        cargarMensajesDesdeBD();

        actualizarUI();

        actualizarEstadisticas();
    }

    private void actualizarUI() {
        cpGridPanel.removeAll();
        JPanel gridPanel = new JPanel(new GridLayout(0, 3, 10, 10)); 
        gridPanel.setBackground(Color.DARK_GRAY);

        List<String> cpIds = new ArrayList<>(cpMap.keySet());
        Collections.sort(cpIds);

        for (String cpId : cpIds) {
            CPInfo cp = cpMap.get(cpId);
            if (cp != null) {
                gridPanel.add(createCPCard(cp));
            }
        }

        cpGridPanel.add(gridPanel);
        cpGridPanel.revalidate();
        cpGridPanel.repaint();

        actualizarTablaPeticiones();
    }

    private void actualizarTablaPeticiones() {
        requestsPanel.removeAll();

        String[] columnNames = {"Fecha", "Hora Inicio", "User ID", "CP"};
        Object[][] data = new Object[driverRequests.size()][4];

        for (int i = 0; i < driverRequests.size(); i++) {
            DriverRequest req = driverRequests.get(i);
            data[i][0] = req.date;
            data[i][1] = req.startTime;
            data[i][2] = req.userId;
            data[i][3] = req.cpId;
        }

        JTable table = new JTable(data, columnNames);
        table.setFont(new Font("Arial", Font.PLAIN, 12));
        table.setRowHeight(25);
        table.getTableHeader().setFont(new Font("Arial", Font.BOLD, 12));
        table.setBackground(Color.WHITE);
        table.setGridColor(Color.LIGHT_GRAY);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBackground(Color.DARK_GRAY);
        requestsPanel.add(scrollPane, BorderLayout.CENTER);

        requestsPanel.revalidate();
        requestsPanel.repaint();
    }

    private void actualizarEstadisticas() {
        int total = cpMap.size();
        int activos = 0;
        int suministrando = 0;
        int averiados = 0;

        for (CPInfo cp : cpMap.values()) {
            if ("Activado".equals(cp.status) || "Suministrando".equals(cp.status)) {
                activos++;
            }
            if ("Suministrando".equals(cp.status)) {
                suministrando++;
            }
            if ("Averiado".equals(cp.status)) {
                averiados++;
            }
        }

        Component[] components = ((JPanel)mainPanel.getComponent(2)).getComponents();
        for (Component comp : components) {
            if (comp instanceof JLabel) {
                JLabel label = (JLabel) comp;
                String text = label.getText();
                if (text.startsWith("Total CPs:")) {
                    label.setText("Total CPs: " + total);
                } else if (text.startsWith("Activos:")) {
                    label.setText("Activos: " + activos);
                } else if (text.startsWith("Suministrando:")) {
                    label.setText("Suministrando: " + suministrando);
                } else if (text.startsWith("Averiados:")) {
                    label.setText("Averiados: " + averiados);
                }
            }
        }
    }

    @Override
    public void dispose() {
        if (refreshTimer != null) {
            refreshTimer.stop();
        }
        DBManager.close();
        instancia = null;
        super.dispose();
    }

    private static class CPInfo {
        String id;
        String location;
        String consumption;
        String status;
        String statusColor;
        String driverId;
        String driverConsumption;
        String driverCost;

        CPInfo(String id, String location, String consumption, String status, String statusColor) {
            this(id, location, consumption, status, statusColor, null, null, null);
        }

        CPInfo(String id, String location, String consumption, String status,
               String statusColor, String driverId, String driverConsumption, String driverCost) {
            this.id = id;
            this.location = location;
            this.consumption = consumption;
            this.status = status;
            this.statusColor = statusColor;
            this.driverId = driverId;
            this.driverConsumption = driverConsumption;
            this.driverCost = driverCost;
        }
    }

    private static class DriverRequest {
        String date;
        String startTime;
        String userId;
        String cpId;

        DriverRequest(String date, String startTime, String userId, String cpId) {
            this.date = date;
            this.startTime = startTime;
            this.userId = userId;
            this.cpId = cpId;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
                } catch (Exception e) {
                    e.printStackTrace();
                }

                CentralDashboard dashboard = new CentralDashboard();
                dashboard.setVisible(true);
            }
        });
    }
}