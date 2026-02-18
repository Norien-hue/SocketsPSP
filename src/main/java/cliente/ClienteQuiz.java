package cliente;

import model.ProtocoloHTTP;

import java.io.*;
import java.net.*;
import java.util.Scanner;

/**
 * Cliente del Quiz multijugador.
 * Basado en el patron de ClienteChat.java
 *
 * Flujo:
 * 1. Se conecta al servidor y envia su nombre (POST /nombre)
 * 2. Hilo listener escucha mensajes del servidor (preguntas, ranking, etc)
 * 3. Hilo principal lee respuestas del usuario y las envia (POST /respuesta)
 */
public class ClienteQuiz {
    private static final String HOST = "localhost"; // Poner aqui la IP del servidor
    private static final int PUERTO = 8080;

    private Socket socket;
    private PrintWriter salida;
    private BufferedReader entrada;
    private Scanner scanner;
    private volatile boolean conectado = true;
    // Flag para saber si se puede responder (hay pregunta activa)
    private volatile boolean puedeResponder = false;

    public ClienteQuiz() {
        scanner = new Scanner(System.in);
    }

    public void iniciar() {
        try {
            socket = new Socket(HOST, PUERTO);
            entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            salida = new PrintWriter(socket.getOutputStream(), true);

            System.out.println("╔══════════════════════════════════╗");
            System.out.println("║       CLIENTE QUIZ BLOOKET       ║");
            System.out.println("╚══════════════════════════════════╝");
            System.out.println("Conectado al servidor " + HOST + ":" + PUERTO + "\n");

            // Recibir peticion de nombre
            String[] respServ = ProtocoloHTTP.leerRespuesta(entrada);
            if (respServ != null && respServ[1].equals("NOMBRE")) {
                System.out.println(respServ[2]);
            }

            // Enviar nombre
            System.out.print("Tu nombre: ");
            String nombre = scanner.nextLine();
            ProtocoloHTTP.enviarPeticion(salida, "POST", "/nombre", nombre);

            // Recibir bienvenida
            respServ = ProtocoloHTTP.leerRespuesta(entrada);
            if (respServ != null) {
                System.out.println("\n" + respServ[2] + "\n");
            }

            // Lanzar hilo para escuchar mensajes del servidor
            Thread listener = new Thread(new ListenerServidor());
            listener.setDaemon(true);
            listener.start();

            while (conectado) {
                String input = scanner.nextLine();
                if (input == null) break;
                input = input.trim();

                if (input.equalsIgnoreCase("/salir")) {
                    conectado = false;
                    break;
                }

                if (!puedeResponder) {
                    System.out.println("  (Espera a que llegue una pregunta)");
                    continue;
                }

                if (input.length() != 1 || "ABCDabcd".indexOf(input.charAt(0)) == -1) {
                    System.out.println("  Solo puedes responder A, B, C o D");
                    continue;
                }

                ProtocoloHTTP.enviarPeticion(salida, "POST", "/respuesta", input.toUpperCase());
                puedeResponder = false;
            }

        } catch (IOException e) {
            System.err.println("Error de conexion: " + e.getMessage());
        } finally {
            cerrarConexion();
        }
    }// fin iniciar

    private void cerrarConexion() {
        try {
            conectado = false;
            if (scanner != null) scanner.close();
            if (salida != null) salida.close();
            if (entrada != null) entrada.close();
            if (socket != null) socket.close();
            System.out.println("\nDesconectado del servidor.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }// fin cerrarConexion

    /**
     * Hilo que escucha mensajes HTTP del servidor y los muestra en pantalla.
     * Basado en ListenerServidor de ClienteChat.java
     */
    private class ListenerServidor implements Runnable {
        @Override
        public void run() {
            try {
                while (conectado) {
                    String[] respuesta = ProtocoloHTTP.leerRespuesta(entrada);
                    if (respuesta == null) break;

                    String tipo = respuesta[1];
                    String cuerpo = respuesta[2];

                    switch (tipo) {
                        case "PREGUNTA":
                            mostrarPregunta(cuerpo);
                            puedeResponder = true;
                            break;

                        case "CONFIRMACION":
                            System.out.println("  >> " + cuerpo);
                            break;

                        case "RESULTADO":
                            System.out.println("\n  *** " + cuerpo + " ***");
                            break;

                        case "RANKING":
                            mostrarRanking(cuerpo);
                            break;

                        case "NEXT":
                            System.out.println("\n  Siguiente pregunta en breve...\n");
                            break;

                        case "INICIO":
                            System.out.println("  >> " + cuerpo + "\n");
                            break;

                        case "INFO":
                            System.out.println("  [i] " + cuerpo);
                            break;

                        case "ERROR":
                            System.out.println("  [!] " + cuerpo);
                            break;

                        case "FIN":
                            System.out.println("\n╔══════════════════════════════════╗");
                            System.out.println("║         JUEGO TERMINADO          ║");
                            System.out.println("╚══════════════════════════════════╝");
                            mostrarRanking(cuerpo);
                            conectado = false;
                            break;

                        default:
                            System.out.println("  " + cuerpo);
                            break;
                    }
                }
            } catch (IOException e) {
                if (conectado) {
                    System.err.println("Conexion perdida con el servidor");
                }
            }
        }
    }// fin ListenerServidor

    private void mostrarPregunta(String cuerpo) {
        // Formato: numPregunta/total|textoPregunta|opA|opB|opC|opD
        String[] partes = cuerpo.split("\\|");
        if (partes.length >= 6) {
            String numInfo = partes[0]; // "1/5"
            String texto = partes[1];
            String opA = partes[2];
            String opB = partes[3];
            String opC = partes[4];
            String opD = partes[5];

            System.out.println("┌──────────────────────────────────────┐");
            System.out.println("  PREGUNTA " + numInfo);
            System.out.println("  " + texto);
            System.out.println("├──────────────────────────────────────┤");
            System.out.println("    A) " + opA);
            System.out.println("    B) " + opB);
            System.out.println("    C) " + opC);
            System.out.println("    D) " + opD);
            System.out.println("└──────────────────────────────────────┘");
            System.out.print("  Tu respuesta (A/B/C/D): ");
        }
    }

    private void mostrarRanking(String cuerpo) {
        System.out.println("┌──────────────────────────────────────┐");
        System.out.println("  RANKING");
        String[] posiciones = cuerpo.split("\\|");
        for (String pos : posiciones) {
            System.out.println("    " + pos.trim());
        }
        System.out.println("└──────────────────────────────────────┘");
    }

    public static void main(String[] args) {
        ClienteQuiz cliente = new ClienteQuiz();
        cliente.iniciar();
    }// fin main
}// fin clase
