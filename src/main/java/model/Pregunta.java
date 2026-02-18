package model;

public class Pregunta {
    private String texto;
    private String opcionA;
    private String opcionB;
    private String opcionC;
    private String opcionD;
    private char respuestaCorrecta; // 'A', 'B', 'C' o 'D'

    public Pregunta(String texto, String opcionA, String opcionB, String opcionC, String opcionD, char respuestaCorrecta) {
        this.texto = texto;
        this.opcionA = opcionA;
        this.opcionB = opcionB;
        this.opcionC = opcionC;
        this.opcionD = opcionD;
        this.respuestaCorrecta = Character.toUpperCase(respuestaCorrecta);
    }

    public String getTexto() { return texto; }
    public String getOpcionA() { return opcionA; }
    public String getOpcionB() { return opcionB; }
    public String getOpcionC() { return opcionC; }
    public String getOpcionD() { return opcionD; }
    public char getRespuestaCorrecta() { return respuestaCorrecta; }

    // Formato para enviar por HTTP: texto|opA|opB|opC|opD
    public String toMensaje() {
        return texto + "|" + opcionA + "|" + opcionB + "|" + opcionC + "|" + opcionD;
    }

    // Reconstruir pregunta desde mensaje recibido
    public static Pregunta fromMensaje(String mensaje) {
        String[] partes = mensaje.split("\\|");
        if (partes.length >= 5) {
            return new Pregunta(partes[0], partes[1], partes[2], partes[3], partes[4], ' ');
        }
        return null;
    }

    // Parsear una linea CSV: pregunta,opA,opB,opC,opD,respuesta
    public static Pregunta fromCSV(String lineaCSV) {
        String[] partes = lineaCSV.split(",");
        if (partes.length >= 6) {
            return new Pregunta(
                    partes[0].trim(),
                    partes[1].trim(),
                    partes[2].trim(),
                    partes[3].trim(),
                    partes[4].trim(),
                    partes[5].trim().charAt(0)
            );
        }
        return null;
    }
}
