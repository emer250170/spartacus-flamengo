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
	 * Igual a {@link #createUpload(String, String, String, byte[])}, aceitando também WebP quando {@code webp} (09/11: arte
	 * das estampas do Mengo Estúdio, que a loja mostra direto). O Java do servidor não lê WebP: a imagem é conferida pelo
	 * cabeçalho do arquivo e as medidas vêm dele. Não usar para fotos que viram outros tamanhos (jogo, experiência, produto).
	 *
	 * @throws IllegalArgumentException
	 *            arquivo vazio, grande demais ou que não é imagem (JPG, PNG, GIF ou, com webp, WebP)
	 */
	default MediaModel createUpload(final String prefix, final String fileName, final String mime, final byte[] data,
			final boolean webp)
	{
		return createUpload(prefix, fileName, mime, data);
	}

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

	/** Final do código dos conjuntos de mídias das fotos do cadastro de produto (posição 0 a 3 no fim) */
	String PHOTO_CONTAINER_SUFFIX = "-fla-foto";

	/**
	 * Fotos do cadastro de produto da loja (assistente "Novo produto", 08/11): cada foto enviada vira um conjunto de
	 * mídias com os tamanhos da loja ({@code <código>-fla-foto0} a {@code -fla-foto3}). A posição 0 é a principal (Imagem e
	 * Miniatura do produto e primeira da galeria); 1 a 3 são os detalhes, na ordem, depois dela na galeria. Posição sem foto
	 * nova não muda. Salva o produto uma vez e atualiza a busca. Não mexe nas fotos de jogos e experiências.
	 *
	 * @param photos
	 *           até 4 fotos, por posição; null numa posição = mantém o que já existe
	 * @return frase curta (ex.: "4 fotos no produto FLA-X."), ou null quando não fez nada
	 */
	String setProductPhotos(ProductModel product, java.util.List<MediaModel> photos);

	/** Foto atual do produto na posição (0 = principal), no tamanho da listagem, ou null */
	MediaModel productPhoto(ProductModel product, int position);
}
