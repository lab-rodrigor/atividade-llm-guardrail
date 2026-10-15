package br.ufpb.dcx.chamados;

import br.ufpb.dcx.chamados.Classificador.Chamado;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Uma página: formulário para cadastrar um chamado e a lista do que o LLM decidiu. */
public class App {

    // Os chamados ficam só na memória: reiniciar o programa zera a lista.
    private static final List<Chamado> chamados = new CopyOnWriteArrayList<>();

    public static void main(String[] args) {
        if (Llm.API_KEY == null || Llm.API_KEY.isBlank()) {
            System.err.println("Defina a variável de ambiente LLM_API_KEY com a chave que o professor enviou no Discord.");
            System.exit(1);
        }
        Javalin app = Javalin.create().start(7070);

        app.get("/", ctx -> ctx.html(pagina()));

        app.post("/chamados", ctx -> {
            String texto = ctx.formParam("texto");
            if (texto != null && !texto.isBlank()) {
                texto = texto.trim();
                if (texto.length() > 500) texto = texto.substring(0, 500);
                chamados.add(0, Classificador.classificar(texto));   // o mais novo aparece primeiro
            }
            ctx.redirect("/", HttpStatus.SEE_OTHER);
        });
    }

    private static String pagina() {
        StringBuilder linhas = new StringBuilder();
        for (Chamado c : chamados) {
            linhas.append("""
                    <tr><td>%s</td><td>%s</td><td><b>%s</b></td><td>%s<br><b style="color:#b00">%s</b></td>
                    <td><details><summary>ver</summary><pre>%s\n\n%d tokens, %d ms</pre></details></td></tr>
                    """.formatted(esc(c.texto()), esc(c.categoria()), esc(c.prioridade()), esc(c.motivo()), esc(c.alerta()),
                    esc(c.respostaBruta()), c.tokens(), c.ms()));
        }
        StringBuilder bases = new StringBuilder();
        for (Classificador.Base b : Classificador.BASES) {
            bases.append("<li>%s <small>(esperado: %s, %s)</small></li>".formatted(esc(b.texto()), esc(b.categoria()), esc(b.prioridade())));
        }
        return """
                <!doctype html><meta charset="utf-8"><title>Chamados</title>
                <style>body{font:16px sans-serif;max-width:960px;margin:2em auto}
                td,th{border-bottom:1px solid #ccc;padding:6px;text-align:left;vertical-align:top}</style>
                <h1>Chamados</h1>
                <form method="post" action="/chamados">
                  <input name="texto" size="70" maxlength="500" placeholder="Descreva o chamado" autofocus required>
                  <button>Cadastrar</button>
                </form>
                <details><summary>Chamados-base para o ataque</summary>
                  <p>O atacante inclui um destes chamados, sem alterar o texto dele, e tenta fazer o programa classificá-lo diferente do esperado.</p>
                  <ul>%s</ul>
                </details>
                <table><tr><th>Chamado</th><th>Categoria</th><th>Prioridade</th><th>Motivo</th><th>Resposta do LLM</th></tr>%s</table>
                """.formatted(bases, linhas);
    }

    // O texto digitado e a resposta do modelo vão para a página, então precisam ser escapados.
    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
