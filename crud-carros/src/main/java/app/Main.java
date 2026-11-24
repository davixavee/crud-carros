package app;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

    private static final CarroDAO dao = new CarroDAO();
    private static final Path PASTA = Path.of("uploads");
    private static final long LIMITE_BYTES = 5L * 1024 * 1024; // 5 MB
    private static final Set<String> EXTENSOES = Set.of("jpg", "jpeg", "png", "webp", "gif");

    private record Parte(String nome, String arquivo, byte[] dados) {
    }

    public static void main(String[] args) throws Exception {
        Files.createDirectories(PASTA);
        HttpServer servidor = HttpServer.create(new InetSocketAddress(8080), 0);
        servidor.createContext("/", Main::home);
        servidor.createContext("/salvar", Main::salvar);
        servidor.createContext("/excluir", Main::excluir);
        servidor.createContext("/uploads/", Main::imagem);
        servidor.createContext("/style.css", Main::css);
        servidor.start();
        System.out.println("Site no ar: http://localhost:8080");
    }

    private static void home(HttpExchange ex) throws IOException {
        if (!ex.getRequestURI().getPath().equals("/")) {
            responder(ex, 404, "text/plain", "Página no encontrada");
            return;
        }
        try {
            Map<String, String> query = parametros(ex.getRequestURI().getRawQuery());
            Carro editando = null;
            if (query.containsKey("editar")) {
                editando = dao.buscar(Integer.parseInt(query.get("editar")));
            }

            List<Carro> carros = dao.listar();
            StringBuilder linhas = new StringBuilder();
            for (Carro c : carros) {
                String foto = (c.imagem() == null || c.imagem().isBlank())
                        ? "<div class=\"foto sem-foto\">Sem foto</div>"
                        : "<img class=\"foto\" src=\"/uploads/" + esc(c.imagem()) + "\" alt=\""
                                + esc(c.marca() + " " + c.modelo()) + "\">";
                linhas.append("<tr>")
                        .append("<td>").append(foto).append("</td>")
                        .append("<td>").append(esc(c.marca())).append("</td>")
                        .append("<td>").append(esc(c.modelo())).append("</td>")
                        .append("<td>").append(c.ano()).append("</td>")
                        .append("<td>").append(esc(c.cor())).append("</td>")
                        .append("<td class=\"num\">")
                        .append(String.format(Locale.forLanguageTag("pt-BR"), "R$ %,.2f", c.preco())).append("</td>")
                        .append("<td class=\"acoes\">")
                        .append("<a class=\"link\" href=\"/?editar=").append(c.id()).append("\">Editar</a>")
                        .append("<form method=\"post\" action=\"/excluir\" onsubmit=\"return confirm('Excluir este carro?')\">")
                        .append("<input type=\"hidden\" name=\"id\" value=\"").append(c.id()).append("\">")
                        .append("<button class=\"link perigo\" type=\"submit\">Excluir</button></form>")
                        .append("</td></tr>");
            }
            if (carros.isEmpty()) {
                linhas.append(
                        "<tr><td colspan=\"7\" class=\"vazio\">Nenhum carro cadastrado. Preencha o formulário para adicionar o primeiro.</td></tr>");
            }

            String previa = "";
            if (editando != null && editando.imagem() != null && !editando.imagem().isBlank()) {
                previa = "<img class=\"previa\" src=\"/uploads/" + esc(editando.imagem()) + "\" alt=\"Foto atual\">"
                        + "<p class=\"dica\">Escolha outra imagem apenas se quiser trocar a foto.</p>";
            }

            String html = modelo("/templates/index.html")
                    .replace("{{linhas}}", linhas)
                    .replace("{{total}}", String.valueOf(carros.size()))
                    .replace("{{titulo_form}}", editando == null ? "Adicionar carro" : "Editar carro")
                    .replace("{{botao}}", editando == null ? "Adicionar" : "Salvar alteracoes")
                    .replace("{{cancelar}}", editando == null ? "" : "<a class=\"link\" href=\"/\">Cancelar</a>")
                    .replace("{{previa}}", previa)
                    .replace("{{id}}", editando == null ? "" : String.valueOf(editando.id()))
                    .replace("{{marca}}", editando == null ? "" : esc(editando.marca()))
                    .replace("{{modelo}}", editando == null ? "" : esc(editando.modelo()))
                    .replace("{{ano}}", editando == null ? "" : String.valueOf(editando.ano()))
                    .replace("{{cor}}", editando == null ? "" : esc(editando.cor()))
                    .replace("{{preco}}", editando == null ? "" : editando.preco().toPlainString());

            responder(ex, 200, "text/html", html);
        } catch (Exception e) {
            erro(ex, e);
        }
    }

    private static void salvar(HttpExchange ex) throws IOException {
        try {
            String tipo = ex.getRequestHeaders().getFirst("Content-Type");
            String boundary = tipo.substring(tipo.indexOf("boundary=") + 9).replace("\"", "");
            byte[] corpo = ex.getRequestBody().readAllBytes();

            Map<String, String> campos = new HashMap<>();
            String novaImagem = null;

            for (Parte p : multipart(corpo, boundary)) {
                if (p.arquivo() == null) {
                    campos.put(p.nome(), new String(p.dados(), StandardCharsets.UTF_8));
                } else if (p.dados().length > 0) {
                    String ext = extensao(p.arquivo());
                    if (ext == null)
                        throw new IllegalArgumentException("Envie uma imagem JPG, PNG, WEBP ou GIF.");
                    if (p.dados().length > LIMITE_BYTES)
                        throw new IllegalArgumentException("A imagem deve ter no maximo 5 MB.");
                    novaImagem = UUID.randomUUID() + "." + ext;
                    Files.write(PASTA.resolve(novaImagem), p.dados());
                }
            }

            String marca = campos.getOrDefault("marca", "").trim();
            String modelo = campos.getOrDefault("modelo", "").trim();
            int ano = Integer.parseInt(campos.getOrDefault("ano", "").trim());
            String cor = campos.getOrDefault("cor", "").trim();
            BigDecimal preco = new BigDecimal(campos.getOrDefault("preco", "0").trim().replace(",", "."));

            String id = campos.getOrDefault("id", "");
            if (id.isBlank()) {
                dao.inserir(marca, modelo, ano, cor, preco, novaImagem);
            } else {
                int idNum = Integer.parseInt(id);
                Carro antigo = dao.buscar(idNum);
                dao.atualizar(idNum, marca, modelo, ano, cor, preco, novaImagem);
                if (novaImagem != null && antigo != null)
                    apagarArquivo(antigo.imagem());
            }
            redirecionar(ex);
        } catch (Exception e) {
            erro(ex, e);
        }
    }

    private static void excluir(HttpExchange ex) throws IOException {
        try {
            Map<String, String> p = parametros(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            int id = Integer.parseInt(p.get("id"));
            Carro carro = dao.buscar(id);
            dao.excluir(id);
            if (carro != null)
                apagarArquivo(carro.imagem());
            redirecionar(ex);
        } catch (Exception e) {
            erro(ex, e);
        }
    }

    private static void imagem(HttpExchange ex) throws IOException {
        String nome = ex.getRequestURI().getPath().substring("/uploads/".length());

        if (!nome.matches("[a-f0-9\\-]+\\.(jpg|jpeg|png|webp|gif)") || !Files.exists(PASTA.resolve(nome))) {
            responder(ex, 404, "text/plain", "Imagem nao encontrada");
            return;
        }
        String ext = nome.substring(nome.lastIndexOf('.') + 1);
        String tipo = switch (ext) {
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            default -> "image/jpeg";
        };
        byte[] bytes = Files.readAllBytes(PASTA.resolve(nome));
        ex.getResponseHeaders().set("Content-Type", tipo);
        ex.sendResponseHeaders(200, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    private static void css(HttpExchange ex) throws IOException {
        responder(ex, 200, "text/css", modelo("/static/style.css"));
    }

    private static List<Parte> multipart(byte[] corpo, String boundary) {
        List<Parte> partes = new ArrayList<>();
        byte[] delim = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
        byte[] fimCabecalho = "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);
        Pattern nomePad = Pattern.compile("name=\"([^\"]*)\"");
        Pattern arquivoPad = Pattern.compile("filename=\"([^\"]*)\"");

        int pos = indexOf(corpo, delim, 0);
        while (pos >= 0) {
            int inicio = pos + delim.length;
            if (inicio + 1 < corpo.length && corpo[inicio] == '-' && corpo[inicio + 1] == '-')
                break;
            inicio += 2;
            int prox = indexOf(corpo, delim, inicio);
            if (prox < 0)
                break;
            int sep = indexOf(corpo, fimCabecalho, inicio);

            String cabecalho = new String(corpo, inicio, sep - inicio, StandardCharsets.UTF_8);
            byte[] dados = Arrays.copyOfRange(corpo, sep + 4, prox - 2);

            Matcher n = nomePad.matcher(cabecalho);
            Matcher a = arquivoPad.matcher(cabecalho);
            if (n.find()) {
                partes.add(new Parte(n.group(1), a.find() ? a.group(1) : null, dados));
            }
            pos = prox;
        }
        return partes;
    }

    private static int indexOf(byte[] origem, byte[] alvo, int de) {
        for (int i = de; i <= origem.length - alvo.length; i++) {
            int j = 0;
            while (j < alvo.length && origem[i + j] == alvo[j])
                j++;
            if (j == alvo.length)
                return i;
        }
        return -1;
    }

    private static String extensao(String nomeArquivo) {
        int ponto = nomeArquivo.lastIndexOf('.');
        if (ponto < 0)
            return null;
        String ext = nomeArquivo.substring(ponto + 1).toLowerCase();
        return EXTENSOES.contains(ext) ? ext : null;
    }

    private static void apagarArquivo(String nome) {
        if (nome == null || nome.isBlank())
            return;
        try {
            Files.deleteIfExists(PASTA.resolve(Path.of(nome).getFileName()));
        } catch (IOException ignorado) {
        }
    }

    private static String modelo(String caminho) throws IOException {
        try (InputStream in = Main.class.getResourceAsStream(caminho)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Map<String, String> parametros(String texto) {
        Map<String, String> mapa = new HashMap<>();
        if (texto == null || texto.isBlank())
            return mapa;
        for (String par : texto.split("&")) {
            String[] kv = par.split("=", 2);
            mapa.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                    kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
        }
        return mapa;
    }

    private static String esc(String s) {
        if (s == null)
            return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static void responder(HttpExchange ex, int status, String tipo, String corpo) throws IOException {
        byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", tipo + "; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    private static void redirecionar(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().set("Location", "/");
        ex.sendResponseHeaders(303, -1);
        ex.close();
    }

    private static void erro(HttpExchange ex, Exception e) throws IOException {
        e.printStackTrace();
        responder(ex, 500, "text/plain", "Erro: " + e.getMessage()
                + "\n\nConfira se o MySQL do XAMPP esta ligado, se o banco carros_db existe e se a coluna imagem foi criada.");
    }
}
