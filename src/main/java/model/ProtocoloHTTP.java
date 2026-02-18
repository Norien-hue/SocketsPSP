package model;

import java.io.*;

/**
 * Clase auxiliar para construir y leer peticiones/respuestas HTTP manualmente.
 *
 * Formato de PETICION (Cliente -> Servidor):
 *   POST /ruta HTTP/1.0
 *   Content-Length: N
 *                              <- linea vacia separadora
 *   cuerpo_del_mensaje
 *
 * Formato de RESPUESTA (Servidor -> Cliente):
 *   HTTP/1.0 200 OK
 *   Type: PREGUNTA
 *   Content-Length: N
 *                              <- linea vacia separadora
 *   cuerpo_del_mensaje
 */
public class ProtocoloHTTP {

    // ======================== ENVIAR ========================

    // Enviar peticion HTTP (cliente -> servidor)
    public static void enviarPeticion(PrintWriter salida, String metodo, String ruta, String cuerpo) {
        salida.println(metodo + " " + ruta + " HTTP/1.0");
        if (cuerpo != null && !cuerpo.isEmpty()) {
            salida.println("Content-Length: " + cuerpo.length());
            salida.println(""); // linea vacia separadora
            salida.println(cuerpo);
        } else {
            salida.println("Content-Length: 0");
            salida.println(""); // linea vacia separadora
        }
        salida.flush();
    }

    // Enviar respuesta HTTP (servidor -> cliente)
    public static void enviarRespuesta(PrintWriter salida, int codigo, String tipo, String cuerpo) {
        String razon = (codigo == 200) ? "OK" : (codigo == 400) ? "Bad Request" : "Error";
        salida.println("HTTP/1.0 " + codigo + " " + razon);
        salida.println("Type: " + tipo);
        if (cuerpo != null && !cuerpo.isEmpty()) {
            salida.println("Content-Length: " + cuerpo.length());
            salida.println(""); // linea vacia separadora
            salida.println(cuerpo);
        } else {
            salida.println("Content-Length: 0");
            salida.println(""); // linea vacia separadora
        }
        salida.flush();
    }

    // ======================== RECIBIR ========================

    // Leer peticion HTTP y devolver array: [metodo, ruta, cuerpo]
    public static String[] leerPeticion(BufferedReader entrada) throws IOException {
        // Linea 1: "POST /ruta HTTP/1.0"
        String lineaPeticion = entrada.readLine();
        if (lineaPeticion == null) return null;

        String[] partesLinea = lineaPeticion.split(" ");
        String metodo = partesLinea.length > 0 ? partesLinea[0] : "";
        String ruta = partesLinea.length > 1 ? partesLinea[1] : "";

        // Leer headers hasta linea vacia
        int contentLength = 0;
        String linea;
        while ((linea = entrada.readLine()) != null && !linea.isEmpty()) {
            if (linea.startsWith("Content-Length:")) {
                try {
                    contentLength = Integer.parseInt(linea.split(":")[1].trim());
                } catch (NumberFormatException e) {
                    contentLength = 0;
                }
            }
        }

        // Leer cuerpo si hay contenido
        String cuerpo = "";
        if (contentLength > 0) {
            cuerpo = entrada.readLine();
            if (cuerpo == null) cuerpo = "";
        }

        return new String[]{metodo, ruta, cuerpo};
    }

    // Leer respuesta HTTP y devolver array: [codigo, tipo, cuerpo]
    public static String[] leerRespuesta(BufferedReader entrada) throws IOException {
        // Linea 1: "HTTP/1.0 200 OK"
        String lineaEstado = entrada.readLine();
        if (lineaEstado == null) return null;

        String codigo = "0";
        String[] partesEstado = lineaEstado.split(" ");
        if (partesEstado.length > 1) {
            codigo = partesEstado[1];
        }

        // Leer headers hasta linea vacia
        String tipo = "";
        int contentLength = 0;
        String linea;
        while ((linea = entrada.readLine()) != null && !linea.isEmpty()) {
            if (linea.startsWith("Type:")) {
                tipo = linea.split(":")[1].trim();
            }
            if (linea.startsWith("Content-Length:")) {
                try {
                    contentLength = Integer.parseInt(linea.split(":")[1].trim());
                } catch (NumberFormatException e) {
                    contentLength = 0;
                }
            }
        }

        // Leer cuerpo
        String cuerpo = "";
        if (contentLength > 0) {
            cuerpo = entrada.readLine();
            if (cuerpo == null) cuerpo = "";
        }

        return new String[]{codigo, tipo, cuerpo};
    }
}
