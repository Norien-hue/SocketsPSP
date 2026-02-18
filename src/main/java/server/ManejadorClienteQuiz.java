package server;

import model.Pregunta;
import model.ProtocoloHTTP;

import java.io.*;
import java.net.*;

/**
 * Hilo que maneja la comunicacion con un cliente individual.
 * Basado en el patron de ManejadorCliente.java y ManejadorClienteChat.java
 */
public class ManejadorClienteQuiz implements Runnable {
    private Socket socket;
    private PrintWriter salida;
    private BufferedReader entrada;
    private String nombreUsuario;
    private boolean conectado = true;

    // Respuesta del cliente a la pregunta actual
    private char respuestaActual = ' ';
    // Momento en que se envio la pregunta (para calcular velocidad)
    private long tiempoEnvioPregunta = 0;
    // Tiempo que tardo en responder (milisegundos)
    private long tiempoRespuesta = Long.MAX_VALUE;
    // Puntuacion acumulada
    private int puntuacion = 0;
    // Flag para saber si ya respondio a la pregunta actual
    private boolean haRespondido = false;

    public ManejadorClienteQuiz(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            salida = new PrintWriter(socket.getOutputStream(), true);

            // Enviar peticion de nombre al cliente
            ProtocoloHTTP.enviarRespuesta(salida, 200, "NOMBRE", "Introduce tu nombre de usuario:");

            // Leer peticion POST /nombre del cliente
            String[] peticion = ProtocoloHTTP.leerPeticion(entrada);
            if (peticion != null && peticion[1].equals("/nombre")) {
                nombreUsuario = peticion[2];
            }

            if (nombreUsuario == null || nombreUsuario.trim().isEmpty()) {
                nombreUsuario = "Jugador_" + socket.getPort();
            }

            System.out.println("[+] " + nombreUsuario + " se ha conectado desde " + socket.getInetAddress());

            // Confirmar conexion
            ProtocoloHTTP.enviarRespuesta(salida, 200, "BIENVENIDA",
                    "Bienvenido " + nombreUsuario + "! Esperando a que comience el juego...");

            // Notificar al servidor
            ServidorQuiz.notificarConexion(nombreUsuario);

            // Bucle principal: escuchar respuestas del cliente
            while (conectado) {
                peticion = ProtocoloHTTP.leerPeticion(entrada);
                if (peticion == null) {
                    // Cliente se desconecto
                    break;
                }

                String metodo = peticion[0];
                String ruta = peticion[1];
                String cuerpo = peticion[2];

                if (ruta.equals("/respuesta") && metodo.equals("POST")) {
                    procesarRespuesta(cuerpo);
                }
            }

        } catch (IOException e) {
            if (conectado) {
                System.out.println("[-] Error con cliente " + nombreUsuario + ": " + e.getMessage());
            }
        } finally {
            desconectar();
        }
    }

    private void procesarRespuesta(String cuerpo) {
        if (haRespondido) {
            ProtocoloHTTP.enviarRespuesta(salida, 400, "ERROR", "Ya has respondido a esta pregunta");
            return;
        }

        if (cuerpo == null || cuerpo.trim().isEmpty()) {
            ProtocoloHTTP.enviarRespuesta(salida, 400, "ERROR", "Respuesta vacia");
            return;
        }

        char respuesta = Character.toUpperCase(cuerpo.trim().charAt(0));

        if (respuesta != 'A' && respuesta != 'B' && respuesta != 'C' && respuesta != 'D') {
            ProtocoloHTTP.enviarRespuesta(salida, 400, "ERROR", "Respuesta invalida. Solo A, B, C o D");
            return;
        }

        respuestaActual = respuesta;
        haRespondido = true;
        tiempoRespuesta = System.currentTimeMillis() - tiempoEnvioPregunta;

        ProtocoloHTTP.enviarRespuesta(salida, 200, "CONFIRMACION",
                "Respuesta " + respuesta + " recibida en " + tiempoRespuesta + "ms");

        System.out.println("    " + nombreUsuario + " respondio: " + respuesta + " (" + tiempoRespuesta + "ms)");

        // avisar al servidor que este cliente ya respondio
        ServidorQuiz.clienteRespondio();
    }


    public void enviarPregunta(Pregunta pregunta, int numeroPregunta, int totalPreguntas) {
        haRespondido = false;
        respuestaActual = ' ';
        tiempoRespuesta = Long.MAX_VALUE;
        tiempoEnvioPregunta = System.currentTimeMillis();

        String cuerpo = numeroPregunta + "/" + totalPreguntas + "|" + pregunta.toMensaje();
        ProtocoloHTTP.enviarRespuesta(salida, 200, "PREGUNTA", cuerpo);
    }

    // enviar ranking
    public void enviarRanking(String ranking) {
        ProtocoloHTTP.enviarRespuesta(salida, 200, "RANKING", ranking);
    }

    //NEXT (siguiente pregunta)
    public void enviarNext() {
        ProtocoloHTTP.enviarRespuesta(salida, 200, "NEXT", "Siguiente pregunta...");
    }

    // Enviar resultado de la pregunta (correcta/incorrecta)
    public void enviarResultado(boolean correcta, int puntosGanados) {
        String msg = correcta
                ? "CORRECTA! +" + puntosGanados + " puntos"
                : "INCORRECTA. +0 puntos";
        ProtocoloHTTP.enviarRespuesta(salida, 200, "RESULTADO", msg);
    }

    // Enviar fin del juego
    public void enviarFinJuego(String rankingFinal) {
        ProtocoloHTTP.enviarRespuesta(salida, 200, "FIN", rankingFinal);
        conectado = false;
    }

    // Enviar mensaje generico
    public void enviarMensaje(String tipo, String mensaje) {
        ProtocoloHTTP.enviarRespuesta(salida, 200, tipo, mensaje);
    }

    // ======================== GETTERS ========================

    public String getNombreUsuario() { return nombreUsuario; }
    public char getRespuestaActual() { return respuestaActual; }
    public long getTiempoRespuesta() { return tiempoRespuesta; }
    public int getPuntuacion() { return puntuacion; }
    public boolean haRespondido() { return haRespondido; }

    public void sumarPuntos(int puntos) {
        this.puntuacion += puntos;
    }

    // ======================== DESCONEXION ========================

    private void desconectar() {
        try {
            conectado = false;
            ServidorQuiz.removerCliente(this);
            if (nombreUsuario != null) {
                System.out.println("[-] " + nombreUsuario + " se ha desconectado");
            }
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
