import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;

public class HttpServerTest {
    public static void main(final String... args) throws IOException {
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
        /* Controlamos el contexto que hará peticiones REST a nuestro servicio */
        server.createContext("/pruebas/", he -> {
            try {
                /* Obtenemos el método usado (en mayúsculas, por si se recibe de otra forma) para saber qué hacer */
                switch (he.getRequestMethod().toUpperCase()) {
                    case "GET":
                        /* Devolvemos un JSON (mal escapado, ojo) con el valor de la URL sobrante */
                        final String responseBody = "['solicitado','" + he.getRequestURI().getPath().substring(he.getHttpContext().getPath().length()).replace("'", "\\'") + "']";
                        he.getResponseHeaders().set("Content-Type", String.format("application/json; charset=%s", StandardCharsets.UTF_8));
                        final byte[] rawResponseBody = responseBody.getBytes(StandardCharsets.UTF_8);
                        he.sendResponseHeaders(HttpURLConnection.HTTP_OK, rawResponseBody.length);
                        he.getResponseBody().write(rawResponseBody);
                        break;
                    case "POST":
                        he.sendResponseHeaders(HttpURLConnection.HTTP_OK, -1);
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
            } finally {
                he.close();
            }
        });
        /* Efectuamos el arranque del servidor, quedando la ejecución bloqueada a partir de aquí */
        server.start();
    }
}