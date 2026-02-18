package server;

import model.Pregunta;
import server.ManejadorClienteQuiz;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Servidor del Quiz multijugador.
 * Basado en el patron de ServidorChat.java
 *
 * Flujo del juego:
 * 1. Se cargan preguntas (desde CSV por FTP o preguntas por defecto)
 * 2. Los clientes se conectan y registran su nombre
 * 3. El admin escribe "iniciar" en la consola del servidor para empezar
 * 4. Para cada pregunta:
 *    a. Se envia la pregunta a todos los clientes (HTTP Response Type: PREGUNTA)
 *    b. Los clientes responden con POST /respuesta (1 char: A/B/C/D)
 *    c. Se calcula ranking por velocidad de respuesta
 *    d. Se envia ranking (HTTP Response Type: RANKING)
 *    e. Se envia NEXT para pasar a siguiente pregunta
 * 5. Al final se envia ranking final (HTTP Response Type: FIN)
 */
public class ServidorQuiz {
    private static final int PUERTO = 8080;
    private static final int MAX_CLIENTES = 10;
    // Puntos maximos por respuesta correcta (disminuyen segun tiempo)
    private static final int PUNTOS_MAX = 1000;
    // Tiempo maximo para responder (milisegundos)
    private static final int TIMEOUT_RESPUESTA = 15000; // 15 segundos

    // Lista thread-safe de clientes conectados
    private static Set<ManejadorClienteQuiz> clientes = ConcurrentHashMap.newKeySet();
    // Contador de respuestas recibidas por pregunta
    private static int respuestasRecibidas = 0;
    private static final Object lockRespuestas = new Object();
    // Lista de preguntas
    private static List<Pregunta> preguntas = new ArrayList<>();

    // Direccion FTP para cargar CSV (para nota 6+)
    private static final String FTP_HOST = "80.225.190.216";
    private static final int FTP_PUERTO = 21;

    public static void main(String[] args) {
        ExecutorService pool = Executors.newFixedThreadPool(MAX_CLIENTES);

        System.out.println("╔══════════════════════════════════╗");
        System.out.println("║      SERVIDOR QUIZ BLOOKET       ║");
        System.out.println("╚══════════════════════════════════╝");

        // Intentar cargar preguntas desde FTP, si falla usar las por defecto
        cargarPreguntas();

        System.out.println("[*] Servidor iniciado en puerto " + PUERTO);
        System.out.println("[*] " + preguntas.size() + " preguntas cargadas");
        System.out.println("[*] Esperando jugadores...");
        System.out.println("[*] Escribe 'iniciar' para empezar el juego\n");

        // Hilo para aceptar conexiones
        Thread hiloConexiones = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(PUERTO)) {
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    ManejadorClienteQuiz manejador = new ManejadorClienteQuiz(clientSocket);
                    clientes.add(manejador);
                    pool.execute(manejador);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        hiloConexiones.setDaemon(true);
        hiloConexiones.start();

        // Hilo principal: espera comando del admin para iniciar
        Scanner scannerAdmin = new Scanner(System.in);
        while (true) {
            String comando = scannerAdmin.nextLine();
            if (comando.equalsIgnoreCase("iniciar")) {
                if (clientes.isEmpty()) {
                    System.out.println("[!] No hay jugadores conectados. Espera a que se conecten.");
                } else {
                    System.out.println("\n[*] JUEGO INICIADO con " + clientes.size() + " jugadores!\n");
                    iniciarJuego(scannerAdmin);
                    break;
                }
            }
        }

        scannerAdmin.close();
        pool.shutdown();
        System.out.println("\n[*] Servidor cerrado.");
    }// fin main

    // ======================== LOGICA DEL JUEGO ========================

    private static void iniciarJuego(Scanner scannerAdmin) {
        // Avisar a todos que empieza el juego
        for (ManejadorClienteQuiz cliente : clientes) {
            cliente.enviarMensaje("INICIO", "El juego va a comenzar! " + preguntas.size() + " preguntas.");
        }

        // Pausa breve
        esperar(2000);

        // Iterar por cada pregunta
        for (int i = 0; i < preguntas.size(); i++) {
            Pregunta pregunta = preguntas.get(i);

            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("Pregunta " + (i + 1) + "/" + preguntas.size() + ": " + pregunta.getTexto());
            System.out.println("  A) " + pregunta.getOpcionA());
            System.out.println("  B) " + pregunta.getOpcionB());
            System.out.println("  C) " + pregunta.getOpcionC());
            System.out.println("  D) " + pregunta.getOpcionD());
            System.out.println("  Correcta: " + pregunta.getRespuestaCorrecta());
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

            // Resetear contador de respuestas
            synchronized (lockRespuestas) {
                respuestasRecibidas = 0;
            }

            // Enviar pregunta a todos
            for (ManejadorClienteQuiz cliente : clientes) {
                cliente.enviarPregunta(pregunta, i + 1, preguntas.size());
            }

            // Esperar a que todos respondan o se acabe el tiempo
            esperarRespuestas();

            // Calcular puntos y ranking para esta pregunta
            calcularPuntos(pregunta);

            // Generar y enviar ranking
            String ranking = generarRanking();
            System.out.println("\n" + ranking);

            for (ManejadorClienteQuiz cliente : clientes) {
                cliente.enviarRanking(ranking);
            }

            // Si no es la ultima pregunta, esperar a que el admin escriba NEXT
            if (i < preguntas.size() - 1) {
                System.out.println("\n[*] Escribe NEXT para pasar a la siguiente pregunta");
                while (true) {
                    String cmd = scannerAdmin.nextLine();
                    if (cmd.equalsIgnoreCase("NEXT")) {
                        break;
                    }
                    System.out.println("[!] Escribe NEXT para continuar");
                }
                // Avisar a los clientes que se pasa a la siguiente
                for (ManejadorClienteQuiz cliente : clientes) {
                    cliente.enviarNext();
                }
                esperar(1000);
            }
        }// fin for preguntas

        // Enviar ranking final
        esperar(2000);
        String rankingFinal = "=== RANKING FINAL ===\n" + generarRanking();
        System.out.println("\n" + rankingFinal);

        for (ManejadorClienteQuiz cliente : clientes) {
            cliente.enviarFinJuego(rankingFinal);
        }
    }// fin iniciarJuego

    // Esperar a que todos los clientes respondan o se acabe el timeout
    private static void esperarRespuestas() {
        long inicio = System.currentTimeMillis();
        while (true) {
            synchronized (lockRespuestas) {
                if (respuestasRecibidas >= clientes.size()) {
                    System.out.println("  Todos han respondido!");
                    break;
                }
            }
            if (System.currentTimeMillis() - inicio > TIMEOUT_RESPUESTA) {
                System.out.println("  Tiempo agotado!");
                break;
            }
            esperar(100); // Comprobar cada 100ms
        }
    }

    // Calcular puntos segun velocidad: mas rapido = mas puntos
    private static void calcularPuntos(Pregunta pregunta) {
        for (ManejadorClienteQuiz cliente : clientes) {
            if (cliente.haRespondido() && cliente.getRespuestaActual() == pregunta.getRespuestaCorrecta()) {
                // Puntos inversamente proporcionales al tiempo de respuesta
                // Respuesta instantanea = PUNTOS_MAX, respuesta en TIMEOUT = ~100 puntos
                long tiempo = cliente.getTiempoRespuesta();
                int puntos = (int) Math.max(100,
                        PUNTOS_MAX - (tiempo * (PUNTOS_MAX - 100) / TIMEOUT_RESPUESTA));
                cliente.sumarPuntos(puntos);
                cliente.enviarResultado(true, puntos);
            } else {
                cliente.enviarResultado(false, 0);
            }
        }
    }

    // Generar string del ranking ordenado por puntuacion
    private static String generarRanking() {
        // Crear lista ordenable de clientes
        List<ManejadorClienteQuiz> listaOrdenada = new ArrayList<>(clientes);
        // Ordenar por puntuacion descendente
        for (int i = 0; i < listaOrdenada.size() - 1; i++) {
            for (int j = 0; j < listaOrdenada.size() - i - 1; j++) {
                if (listaOrdenada.get(j).getPuntuacion() < listaOrdenada.get(j + 1).getPuntuacion()) {
                    ManejadorClienteQuiz temp = listaOrdenada.get(j);
                    listaOrdenada.set(j, listaOrdenada.get(j + 1));
                    listaOrdenada.set(j + 1, temp);
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < listaOrdenada.size(); i++) {
            ManejadorClienteQuiz c = listaOrdenada.get(i);
            sb.append((i + 1)).append(". ")
                    .append(c.getNombreUsuario())
                    .append(" - ")
                    .append(c.getPuntuacion())
                    .append(" pts");
            if (i < listaOrdenada.size() - 1) sb.append(" | ");
        }
        return sb.toString();
    }

    // ======================== CARGA DE PREGUNTAS ========================

    private static void cargarPreguntas() {
        // Intentar cargar desde FTP
        System.out.println("[*] Intentando cargar preguntas desde FTP " + FTP_HOST + "...");
        List<Pregunta> preguntasFTP = cargarDesdeCSV_FTP();

        if (preguntasFTP != null && !preguntasFTP.isEmpty()) {
            preguntas = preguntasFTP;
            System.out.println("[*] Preguntas cargadas desde FTP correctamente!");
        } else {
            System.out.println("[!] No se pudo cargar desde FTP. Usando preguntas por defecto.");
            cargarPreguntasPorDefecto();
        }

        Collections.shuffle(preguntas);
    }

    /**
     * Cargar preguntas desde un archivo CSV en servidor FTP.
     * Usa conexion FTP manual con Sockets (basado en los patrones del curso).
     * Formato CSV esperado: pregunta,opcionA,opcionB,opcionC,opcionD,respuestaCorrecta
     */
    private static List<Pregunta> cargarDesdeCSV_FTP() {
        List<Pregunta> lista = new ArrayList<>();
        Socket socketFTP = null;
        BufferedReader lectorFTP = null;
        PrintWriter escritorFTP = null;

        try {
            // Conexion al servidor FTP (puerto 21)
            socketFTP = new Socket(FTP_HOST, FTP_PUERTO);
            lectorFTP = new BufferedReader(new InputStreamReader(socketFTP.getInputStream()));
            escritorFTP = new PrintWriter(socketFTP.getOutputStream(), true);

            // Leer mensaje de bienvenida del FTP
            String respuesta = lectorFTP.readLine();
            System.out.println("  FTP: " + respuesta);

            // Login anonimo
            escritorFTP.println("USER anonymous");
            respuesta = lectorFTP.readLine();
            System.out.println("  FTP: " + respuesta);

            escritorFTP.println("PASS anonymous@");
            respuesta = lectorFTP.readLine();
            System.out.println("  FTP: " + respuesta);

            // Modo pasivo para transferencia de datos
            escritorFTP.println("PASV");
            respuesta = lectorFTP.readLine();
            System.out.println("  FTP: " + respuesta);

            // Parsear IP y puerto del modo pasivo: 227 Entering Passive Mode (h1,h2,h3,h4,p1,p2)
            int inicioParentesis = respuesta.indexOf('(');
            int finParentesis = respuesta.indexOf(')');
            if (inicioParentesis == -1 || finParentesis == -1) {
                System.out.println("  [!] No se pudo parsear respuesta PASV");
                return null;
            }
            String[] numeros = respuesta.substring(inicioParentesis + 1, finParentesis).split(",");
            String ipDatos = numeros[0] + "." + numeros[1] + "." + numeros[2] + "." + numeros[3];
            int puertoDatos = Integer.parseInt(numeros[4]) * 256 + Integer.parseInt(numeros[5]);

            // Solicitar el archivo CSV
            escritorFTP.println("RETR preguntas.csv");
            respuesta = lectorFTP.readLine();
            System.out.println("  FTP: " + respuesta);

            // Conectar al puerto de datos para recibir el archivo
            Socket socketDatos = new Socket(ipDatos, puertoDatos);
            BufferedReader lectorDatos = new BufferedReader(
                    new InputStreamReader(socketDatos.getInputStream())
            );

            // Leer CSV linea por linea
            String linea;
            boolean primeraLinea = true;
            while ((linea = lectorDatos.readLine()) != null) {
                // Saltar cabecera si existe
                if (primeraLinea) {
                    primeraLinea = false;
                    // Si la primera linea no parece una pregunta valida, es cabecera
                    if (linea.toLowerCase().contains("pregunta") || linea.toLowerCase().contains("question")) {
                        continue;
                    }
                }
                if (!linea.trim().isEmpty()) {
                    Pregunta p = Pregunta.fromCSV(linea);
                    if (p != null) {
                        lista.add(p);
                    }
                }
            }

            // Cerrar conexion de datos
            lectorDatos.close();
            socketDatos.close();

            // Leer confirmacion de transferencia
            respuesta = lectorFTP.readLine();
            System.out.println("  FTP: " + respuesta);

            // Cerrar sesion FTP
            escritorFTP.println("QUIT");
            respuesta = lectorFTP.readLine();
            System.out.println("  FTP: " + respuesta);

        } catch (IOException e) {
            System.out.println("  [!] Error FTP: " + e.getMessage());
            return null;
        } finally {
            try {
                if (lectorFTP != null) lectorFTP.close();
                if (escritorFTP != null) escritorFTP.close();
                if (socketFTP != null) socketFTP.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return lista;
    }

    // Preguntas por defecto si no se puede acceder al FTP
    private static void cargarPreguntasPorDefecto() {
        preguntas.add(new Pregunta(
                "Que protocolo usa la web para transferir paginas?",
                "FTP", "HTTP", "SMTP", "SSH", 'B'));
        preguntas.add(new Pregunta(
                "Que puerto usa por defecto el protocolo HTTP?",
                "21", "443", "80", "8080", 'C'));
        preguntas.add(new Pregunta(
                "Que clase de Java se usa para crear un servidor TCP?",
                "Socket", "ServerSocket", "DatagramSocket", "URLConnection", 'B'));
        preguntas.add(new Pregunta(
                "Cual de estos NO es un metodo HTTP?",
                "GET", "POST", "SEND", "DELETE", 'C'));
        preguntas.add(new Pregunta(
                "Que significa TCP?",
                "Transfer Control Protocol", "Transmission Control Protocol",
                "Technical Communication Protocol", "Transport Connection Protocol", 'B'));
    }

    // ======================== METODOS PARA LOS MANEJADORES ========================

    // Notificar que un cliente respondio (llamado desde ManejadorClienteQuiz)
    public static void clienteRespondio() {
        synchronized (lockRespuestas) {
            respuestasRecibidas++;
        }
    }

    // Notificar nueva conexion
    public static void notificarConexion(String nombre) {
        System.out.println("[*] Jugadores conectados: " + clientes.size());
        // Avisar a todos los demas
        for (ManejadorClienteQuiz cliente : clientes) {
            if (!cliente.getNombreUsuario().equals(nombre)) {
                cliente.enviarMensaje("INFO", nombre + " se ha unido! (" + clientes.size() + " jugadores)");
            }
        }
    }

    // Remover cliente desconectado
    public static void removerCliente(ManejadorClienteQuiz cliente) {
        clientes.remove(cliente);
    }

    // ======================== UTILIDADES ========================

    private static void esperar(int milisegundos) {
        try {
            Thread.sleep(milisegundos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}// fin clase