import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import com.google.gson.*;

/*
Descargar la última versión GSON (probada la versión 2.8.2) desde:
  https://repo1.maven.org/maven2/com/google/code/gson/gson/

Compilar:
  javac -cp gson-2.8.2.jar:. HttpServerTest.java

Ejecutar:
  java -cp gson-2.8.2.jar:. HttpServerTest
*/

public class HttpServerTest {
    /* Clase privada necesaria para generar un mensaje de respuesta en JSON */
    private class RespuestaMensaje {
        private String mensaje;
        private boolean error = false;

        public RespuestaMensaje(String mensaje, boolean error) {
            this.mensaje = mensaje;
            this.error = error;
        }
    }

    /* Clase privada necesaria para obtener una consulta en JSON */
    private class ConsultaMensaje {
        private String mensaje;

        public String getMensaje() {
            return mensaje;
        }
    }

    /* Instanciamos esta clase y la ejecutamos */
    public static void main(final String... args) throws IOException {
       HttpServerTest http = new HttpServerTest();
       http.ejecutame();
    }

    public void ejecutame() throws IOException {
        final HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", 8080), 10);
        /* Controlamos el contexto general para descargar archivos estáticos en la ruta actual */
        server.createContext("/", he -> {
            try {
                /* Comprobamos la existencia del archivo a partir de la ruta www del directorio actual ("./www") */
                File file = new File("./www", he.getRequestURI().getPath());
                /* Si es un directorio cargamos el index.html (dará "not found" si éste no existe) */
                if (file.isDirectory()) {
                    file = new File(file, "index.html");
                }
                if (file.exists()) {
                    /* Obtenemos el tipo mime del archivo para enviarlo en la cabecera correspondiente */
                    he.getResponseHeaders().set("Content-Type", Files.probeContentType(Paths.get(file.getPath())));
                    /* Enviamos las cabeceras HTTP OK junto con la longitud del contenido */
                    he.sendResponseHeaders(HttpURLConnection.HTTP_OK, file.length());
                    /* Para no saturar la memoria ni el recolector de basura enviamos el archivo en trozos de 64K */
                    OutputStream output = he.getResponseBody();
                    FileInputStream fs = new FileInputStream(file);
                    final byte[] buffer = new byte[0x10000];
                    int count = 0;
                    while ((count = fs.read(buffer)) >= 0) {
                        output.write(buffer, 0, count);
                    }
                    output.flush();
                    output.close();
                    fs.close();
                } else {
                    /* Si el archivo no existe lo indicamos así en el código de respuesta y el mensaje */
                    String response = "Error 404: El archivo \"" + he.getRequestURI().getPath() + "\" no existe.";
                    he.sendResponseHeaders(HttpURLConnection.HTTP_NOT_FOUND, response.length());
                    OutputStream output = he.getResponseBody();
                    output.write(response.getBytes());
                    output.flush();
                    output.close();
                }
            } finally {
                he.close();
            }
        });
        /* Controlamos el contexto que hará peticiones REST/JSON a nuestro servicio */
        server.createContext("/json/", he -> {
            try {
                /* Definimos las variables de uso común */
                Gson gson = new Gson();
                final String responseBody;
                final byte[] rawResponseBody;
                RespuestaMensaje respuesta;
                /* Agregamos un mínimo de información de depuración */
                System.out.println(he.getRequestMethod() + " \"" + he.getRequestURI().getPath() + "\"");
                /* Obtenemos el método usado (en mayúsculas, por si se recibe de otra forma) para saber qué hacer */
                switch (he.getRequestMethod().toUpperCase()) {
                    case "GET":
                        /* Creamos una instancia de Respuesta para ser convertida en JSON */
                        respuesta = new RespuestaMensaje(he.getRequestURI().getPath().substring(he.getHttpContext().getPath().length()), false);
                        /* Creamos un JSON usando GSON */
                        responseBody = gson.toJson(respuesta);
                        /* Enviamos la cabecera HTTP para indicar que la respuesta serán datos JSON */
                        he.getResponseHeaders().set("Content-Type", String.format("application/json; charset=%s", StandardCharsets.UTF_8));
                        /* Convertimos la cadena JSON en una matriz de bytes para ser entregados al navegador */
                        rawResponseBody = responseBody.getBytes(StandardCharsets.UTF_8);
                        he.sendResponseHeaders(HttpURLConnection.HTTP_OK, rawResponseBody.length);
                        he.getResponseBody().write(rawResponseBody);
                        break;
                    case "POST":
                        /* Obtenemos el mensaje enviado mediante POST */
                        java.util.Scanner s = new java.util.Scanner(he.getRequestBody(), StandardCharsets.UTF_8.toString()).useDelimiter("\\A");
                        /* Si no hay ningún problema */
                        if (s.hasNext()) {
                            ConsultaMensaje consulta = gson.fromJson(s.next(), ConsultaMensaje.class);
                            respuesta = new RespuestaMensaje(consulta.getMensaje(), false);
                        } else {
                            respuesta = new RespuestaMensaje("Error recibiendo datos", true);
                        }
                        responseBody = gson.toJson(respuesta);
                        /* Enviamos la cabecera HTTP para indicar que la respuesta serán datos JSON */
                        he.getResponseHeaders().set("Content-Type", String.format("application/json; charset=%s", StandardCharsets.UTF_8));
                        /* Convertimos la cadena JSON en una matriz de bytes para ser entregados al navegador */
                        rawResponseBody = responseBody.getBytes(StandardCharsets.UTF_8);
                        he.sendResponseHeaders(HttpURLConnection.HTTP_OK, rawResponseBody.length);
                        he.getResponseBody().write(rawResponseBody);
                        break;
                    case "DELETE":
                        he.sendResponseHeaders(HttpURLConnection.HTTP_OK, -1);
                        break;
                    case "OPTIONS":
                        he.getResponseHeaders().set("Allow", "GET,POST,DELETE,OPTIONS");
                        he.sendResponseHeaders(HttpURLConnection.HTTP_OK, -1);
                        break;
                    default:
                        /* Si no se selecciona un método correcto devolvemos un BAD METHOD */
                        he.getResponseHeaders().set("Allow", "GET,POST,DELETE,OPTIONS");
                        he.sendResponseHeaders(HttpURLConnection.HTTP_BAD_METHOD, -1);
                        break;
                }
            } catch (Exception e) {
                System.out.println(e.getMessage());
            } finally {
                he.close();
            }
        });
        /* Controlamos el contexto que hará peticiones en texto a nuestro servicio */
        server.createContext("/texto/", he -> {
            try {
                /* Definimos las variables de uso común */
                final String responseBody;
                final byte[] rawResponseBody;
                /* Agregamos un mínimo de información de depuración */
                System.out.println(he.getRequestMethod() + " \"" + he.getRequestURI().getPath() + "\"");
                /* Obtenemos el método usado (en mayúsculas, por si se recibe de otra forma) para saber qué hacer */
                switch (he.getRequestMethod().toUpperCase()) {
                    case "GET":
                        /* Obtenemos la URL restante */
                        responseBody = he.getRequestURI().getPath().substring(he.getHttpContext().getPath().length());
                        /* Enviamos la cabecera HTTP para indicar que la respuesta serán datos en texto plano */
                        he.getResponseHeaders().set("Content-Type", String.format("text/plain; charset=%s", StandardCharsets.UTF_8));
                        /* Convertimos la cadena de texto en una matriz de bytes para ser entregados al navegador */
                        rawResponseBody = responseBody.getBytes(StandardCharsets.UTF_8);
                        he.sendResponseHeaders(HttpURLConnection.HTTP_OK, rawResponseBody.length);
                        he.getResponseBody().write(rawResponseBody);
                        break;
                    case "POST":
                        /* Obtenemos el mensaje enviado mediante POST */
                        java.util.Scanner s = new java.util.Scanner(he.getRequestBody(), StandardCharsets.UTF_8.toString()).useDelimiter("\\A");
                        /* Si no hay ningún problema */
                        if (s.hasNext()) {
                            responseBody = s.next();
                        } else {
                            responseBody = "Error recibiendo datos";
                        }
                        /* Enviamos la cabecera HTTP para indicar que la respuesta serán datos en texto plano */
                        he.getResponseHeaders().set("Content-Type", String.format("text/plain; charset=%s", StandardCharsets.UTF_8));
                        /* Convertimos la cadena en una matriz de bytes para ser entregados al navegador */
                        rawResponseBody = responseBody.getBytes(StandardCharsets.UTF_8);
                        he.sendResponseHeaders(HttpURLConnection.HTTP_OK, rawResponseBody.length);
                        he.getResponseBody().write(rawResponseBody);
                        break;
                    case "DELETE":
                        he.sendResponseHeaders(HttpURLConnection.HTTP_OK, -1);
                        break;
                    case "OPTIONS":
                        he.getResponseHeaders().set("Allow", "GET,POST,DELETE,OPTIONS");
                        he.sendResponseHeaders(HttpURLConnection.HTTP_OK, -1);
                        break;
                    default:
                        /* Si no se selecciona un método correcto devolvemos un BAD METHOD */
                        he.getResponseHeaders().set("Allow", "GET,POST,DELETE,OPTIONS");
                        he.sendResponseHeaders(HttpURLConnection.HTTP_BAD_METHOD, -1);
                        break;
                }
            } catch (Exception e) {
                System.out.println(e.getMessage());
            } finally {
                he.close();
            }
        });
        /* Efectuamos el arranque del servidor, quedando la ejecución bloqueada a partir de aquí */
        server.start();
    }
}
