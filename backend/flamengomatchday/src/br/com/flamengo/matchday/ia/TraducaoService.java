package br.com.flamengo.matchday.ia;

/**
 * Tradução com IA para o cadastro da loja (assistente "Novo produto", 08/11): o gestor escreve em português e o botão
 * "Traduzir com IA" preenche o inglês, que ele revisa antes de salvar. Nada é gravado sem o gestor salvar.
 * <p>
 * Chaves: {@code flamengo.ia.traducao.ativo} (padrão true; sem chave não aparece), {@code flamengo.ia.traducao.chave} (chave da API, só no
 * servidor: local.properties ou HAC › Configuration, nunca no repositório), {@code flamengo.ia.traducao.modelo},
 * {@code flamengo.ia.traducao.url} e {@code flamengo.ia.traducao.timeoutSegundos}. Sem chave ou desligada, o botão não
 * aparece e o cadastro funciona igual.
 */
public interface TraducaoService
{
	/** Ligada e com chave configurada */
	boolean disponivel();

	/**
	 * Traduz um texto curto de loja (nome ou descrição de produto).
	 *
	 * @param texto
	 *           texto de origem; vazio devolve vazio
	 * @param de
	 *           idioma de origem (ex.: pt)
	 * @param para
	 *           idioma de destino (ex.: en)
	 * @return só o texto traduzido
	 * @throws IllegalStateException
	 *            serviço desligado, sem chave, fora do ar ou resposta inesperada (mensagem em português para o gestor)
	 */
	String traduzir(String texto, String de, String para);
}
