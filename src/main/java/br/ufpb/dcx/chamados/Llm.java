package br.ufpb.dcx.chamados;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Fala com o LLM da disciplina (LiteLLM). É a única classe que faz chamada de rede. */
public class Llm {

    // Endereço, chave e modelo vêm de variáveis de ambiente, nunca do código.
    static final String BASE_URL = System.getenv().getOrDefault("LLM_BASE_URL", "https://llm.rodrigor.com/v1");
    static final String API_KEY = System.getenv("LLM_API_KEY");
    static final String MODELO = System.getenv().getOrDefault("LLM_MODEL", "gpt-4o-mini");

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final ObjectMapper JSON = new ObjectMapper();

    /** O que voltou de uma chamada: o texto da resposta, os tokens gastos e o tempo. */
    public record Resposta(String conteudo, int tokens, long ms) {}

    public static Resposta conversar(String instrucao, String mensagem) throws Exception {
        // 1. Monta o corpo da requisição, no formato "chat completions" (o mesmo da OpenAI).
        String corpo = JSON.writeValueAsString(Map.of(
                "model", MODELO,
                "temperature", 0,                                  // 0 = respostas o mais repetíveis possível
                "response_format", Map.of("type", "json_object"),  // pede que a resposta seja um JSON
                "messages", List.of(
                        Map.of("role", "system", "content", instrucao),   // o prompt (arquivo prompt.txt)
                        Map.of("role", "user", "content", mensagem))));   // o chamado digitado na tela

        // 2. Envia para o LiteLLM, com a chave no cabeçalho Authorization.
        HttpRequest requisicao = HttpRequest.newBuilder(URI.create(BASE_URL + "/chat/completions"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + API_KEY)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(corpo))
                .build();
        long inicio = System.currentTimeMillis();
        HttpResponse<String> resposta = HTTP.send(requisicao, HttpResponse.BodyHandlers.ofString());
        long ms = System.currentTimeMillis() - inicio;
        if (resposta.statusCode() != 200) {
            throw new IllegalStateException("o LLM respondeu HTTP " + resposta.statusCode());
        }

        // 3. Tira o texto da resposta: choices[0].message.content
        JsonNode raiz = JSON.readTree(resposta.body());
        return new Resposta(raiz.at("/choices/0/message/content").asText(), raiz.at("/usage/total_tokens").asInt(), ms);
    }
}
