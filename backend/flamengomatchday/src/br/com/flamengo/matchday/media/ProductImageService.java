package br.com.flamengo.matchday.media;

import de.hybris.platform.core.model.media.MediaModel;
import de.hybris.platform.core.model.product.ProductModel;


/**
 * Imagens dos jogos e das experiências (pedido do Emerson, 04/11): o gestor envia uma foto
 * horizontal no Backoffice; ela fica no jogo ou na experiência e é copiada para o produto da
 * loja (JOGO-… e EXP-…), nos tamanhos que a loja usa. As poltronas (variantes do jogo) usam a
 * imagem do produto do jogo, como qualquer variante sem imagem própria.
 * <p>
 * Chave {@code flamengo.produtos.imagem.copiar.ativo} (padrão ligada): desligada, a imagem fica
 * só no jogo ou na experiência e o produto não muda. Sem imagem, nada muda no produto.
 */
public interface ProductImageService
{
	/** Liga/desliga a cópia para o produto */
	String COPY_ENABLED_PROPERTY = "flamengo.produtos.imagem.copiar.ativo";

	/**
	 * Grava uma imagem enviada pelo Backoffice (pasta images, sem catálogo).
	 *
	 * @param prefix
	 *           início do código da mídia (ex.: "jogo", "experiencia")
	 * @throws IllegalArgumentException
	 *            arquivo vazio, grande demais ou que não é imagem (JPG, PNG ou GIF)
	 */
	MediaModel createUpload(String prefix, String fileName, String mime, byte[] data);

	/**
	 * Copia a imagem para o produto: um conjunto de mídias (um tamanho por formato da loja) que vira a
	 * imagem principal e a primeira da galeria. Sem imagem, tira do produto só o que esta cópia tinha
	 * posto. Não salva o produto se nada mudou.
	 *
	 * @return frase curta para a situação do jogo ou da experiência, ou null quando não fez nada
	 */
	String copyToProduct(MediaModel image, ProductModel product);

	/** Endereço da imagem (/medias/...), ou null */
	String url(MediaModel image);
}
