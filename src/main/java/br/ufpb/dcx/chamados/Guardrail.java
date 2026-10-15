package br.ufpb.dcx.chamados;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Lê o GUARDRAIL.md da raiz a cada chamado. O arquivo tem uma seção "Prompt", com o prompt
 * do sistema em um bloco de código, e uma seção "Regras", com linhas "chave: valor".
 */
public class Guardrail {

    public String prompt = "";
    public int limiteCaracteres = 500;
    public int motivoMaxCaracteres = 300;
    public final List<Pattern> bloquearEntrada = new ArrayList<>();
    public final List<Pattern> proibirSaida = new ArrayList<>();

    public static Guardrail carregar() throws Exception {
        Guardrail g = new Guardrail();
        String secao = "";
        boolean dentroDoBloco = false, promptLido = false;
        StringBuilder prompt = new StringBuilder();
        for (String linha : Files.readAllLines(Path.of("GUARDRAIL.md"))) {
            if (linha.startsWith("## ")) {
                secao = linha.substring(3).trim().toLowerCase();
                continue;
            }
            if (secao.equals("prompt")) {
                if (linha.trim().startsWith("```")) {
                    if (dentroDoBloco) promptLido = true;
                    dentroDoBloco = !dentroDoBloco && !promptLido;
                } else if (dentroDoBloco) {
                    prompt.append(linha).append('\n');
                }
            } else if (secao.equals("regras")) {
                int i = linha.indexOf(':');
                if (i < 0) continue;
                String chave = linha.substring(0, i).replaceFirst("^[-*\\s]+", "").trim().toLowerCase();
                String valor = linha.substring(i + 1).trim();
                if (valor.isEmpty()) continue;
                switch (chave) {
                    case "limite_caracteres" -> g.limiteCaracteres = Integer.parseInt(valor);
                    case "motivo_max_caracteres" -> g.motivoMaxCaracteres = Integer.parseInt(valor);
                    case "bloquear_entrada" -> g.bloquearEntrada.add(regex(valor));
                    case "proibir_saida" -> g.proibirSaida.add(regex(valor));
                    default -> { }
                }
            }
        }
        g.prompt = prompt.toString().strip();
        if (g.prompt.isEmpty()) throw new IllegalStateException("GUARDRAIL.md sem prompt na seção \"Prompt\"");
        return g;
    }

    private static Pattern regex(String valor) {
        return Pattern.compile(valor, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    /** Primeira regra de entrada que o texto casa, ou null. */
    public String entradaBloqueada(String texto) {
        if (texto.length() > limiteCaracteres) return "limite_caracteres: " + limiteCaracteres;
        for (Pattern p : bloquearEntrada) if (p.matcher(texto).find()) return "bloquear_entrada: " + p.pattern();
        return null;
    }

    /** Primeira regra de saída que o texto casa, ou null. */
    public String saidaBloqueada(String texto) {
        for (Pattern p : proibirSaida) if (p.matcher(texto).find()) return "proibir_saida: " + p.pattern();
        return null;
    }
}
