package br.com.flamengo.matchday.estudio;

import de.hybris.platform.core.model.ItemModel;
import de.hybris.platform.core.model.product.ProductModel;

import java.util.List;
import java.util.Map;


/**
 * Mengo Estúdio (07/11): regras num lugar só — relógio da demo, situação da estampa pelo prazo de venda,
 * camisetas (modelo e tecido) com preço, tamanhos e medidas, cores bloqueadas pela arte. A loja lê pela API
 * (/mengo-estudio/catalogo) e o carrinho e o fechamento do pedido usam as mesmas regras.
 * Chave geral flamengo.estudio.ativo.
 */
public interface MengoEstudioService
{
	String ENCERRADA = "ENCERRADA";
	String ULTIMAS_48H = "ULTIMAS_48H";
	String ULTIMOS_DIAS = "ULTIMOS_DIAS";
	String NOVA = "NOVA";
	String A_VENDA = "A_VENDA";

	boolean isActive();

	/** Agora pelas regras do Estúdio: hora real + deslocamento do relógio da demo (ms) */
	long now();

	/** Estampas visíveis (ativas), em ordem de encerramento; inclui as encerradas (acervo) */
	List<ItemModel> estampas();

	ItemModel estampa(String code);

	/** Estampa ligada (active) e já lançada: aparece na loja, à venda ou no acervo */
	boolean isVisible(ItemModel estampa);

	/** Fim da venda da estampa (ms), com o encerramento simulado pela demo quando houver */
	long end(ItemModel estampa);

	/** ENCERRADA, ULTIMAS_48H, ULTIMOS_DIAS, NOVA ou A_VENDA */
	String situacao(ItemModel estampa, long now);

	boolean onSale(ItemModel estampa);

	/** Cor (PRT, BRA, CIN) -> motivo, das cores indisponíveis para a arte */
	Map<String, String> blockedColors(ItemModel estampa);

	/** Camisetas do Estúdio no catálogo Online (produtos com mengoModelo) */
	List<ProductModel> camisetas();

	ProductModel camiseta(String modelo, String tecido);

	/** Tamanhos válidos para o modelo e o tecido (vazio = combinação não vendida) */
	List<String> tamanhos(String modelo, String tecido);

	/** Medidas: tamanho -> [largura, comprimento] em cm (exemplo, de propriedade) */
	Map<String, int[]> medidas(String modelo, String tecido);

	/** Dados para a loja (drops, linhas, estampas, camisetas, regras) */
	Map<String, Object> catalogo();

	Map<String, Object> estampaDto(ItemModel estampa, long now);

	/** Painel da demo: configuração atual ou null quando não há simulação */
	ItemModel demoConfig();
}
