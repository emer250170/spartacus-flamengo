package br.com.flamengo.matchday.loja;

import br.com.flamengo.matchday.media.ProductImageService;
import de.hybris.platform.category.model.CategoryModel;
import de.hybris.platform.core.model.media.MediaModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.model.attribute.DynamicAttributeHandler;

import java.util.Locale;

import org.apache.commons.lang3.StringUtils;


/**
 * Campos do cadastro simples do produto (assistente "Novo produto" e aba "Cadastro", 08/11). Cada atributo dinâmico
 * mostra o valor que a loja usa e grava onde a loja lê:
 * <ul>
 * <li>nomePt, nomeEn, resumoPt, resumoEn: nome e resumo do produto em cada idioma (o resumo também vira a descrição
 * quando ela está vazia ou era igual ao resumo);</li>
 * <li>categoria: a categoria da loja (as técnicas, como tamanho, ficam);</li>
 * <li>preco, estoque, foto0..foto3: mostram o atual e guardam o novo como pendente (flaPrecoNovo, flaEstoqueNovo,
 * flaFotoNova0..3), aplicado logo depois de gravar.</li>
 * </ul>
 */
public class ProdutoCadastroCampoHandler implements DynamicAttributeHandler<Object, ProductModel>
{
	private static final Locale PT = new Locale("pt");
	private static final Locale EN = Locale.ENGLISH;

	private ProdutoCadastroService produtoCadastroService;
	private ProductImageService productImageService;
	private ModelService modelService;
	/** nomePt, nomeEn, resumoPt, resumoEn, categoria, preco, estoque, foto0, foto1, foto2, foto3 */
	private String campo;

	@Override
	public Object get(final ProductModel product)
	{
		if (product == null)
		{
			return null;
		}
		switch (campo)
		{
			case "nomePt":
				return product.getName(PT);
			case "nomeEn":
				return product.getName(EN);
			case "resumoPt":
				return product.getSummary(PT);
			case "resumoEn":
				return product.getSummary(EN);
			case "categoria":
				return produtoCadastroService.categoriaDaLoja(product);
			case "preco":
			{
				final Object novo = pendente(product, ProdutoCadastroService.PRECO_NOVO);
				return novo != null ? novo : produtoCadastroService.precoAtual(product);
			}
			case "estoque":
			{
				final Object novo = pendente(product, ProdutoCadastroService.ESTOQUE_NOVO);
				return novo != null ? novo : produtoCadastroService.estoqueAtual(product);
			}
			default:
				if (campo.startsWith("foto"))
				{
					final int position = Integer.parseInt(campo.substring(4));
					final Object nova = pendente(product, ProdutoCadastroService.FOTO_NOVA + position);
					return nova != null ? nova : productImageService.productPhoto(product, position);
				}
				return null;
		}
	}

	@Override
	public void set(final ProductModel product, final Object value)
	{
		switch (campo)
		{
			case "nomePt":
				product.setName(texto(value), PT);
				break;
			case "nomeEn":
				product.setName(texto(value), EN);
				break;
			case "resumoPt":
				resumo(product, texto(value), PT);
				break;
			case "resumoEn":
				resumo(product, texto(value), EN);
				break;
			case "categoria":
				produtoCadastroService.definirCategoriaDaLoja(product, value instanceof CategoryModel ? (CategoryModel) value : null);
				break;
			case "preco":
				modelService.setAttributeValue(product, ProdutoCadastroService.PRECO_NOVO, value instanceof Number
						? Double.valueOf(((Number) value).doubleValue()) : null);
				break;
			case "estoque":
				modelService.setAttributeValue(product, ProdutoCadastroService.ESTOQUE_NOVO, value instanceof Number
						? Integer.valueOf(((Number) value).intValue()) : null);
				break;
			default:
				if (campo.startsWith("foto"))
				{
					// sem foto nova (Remover) = mantém a atual; tirar foto do produto é na aba Multimídia
					modelService.setAttributeValue(product, ProdutoCadastroService.FOTO_NOVA + campo.substring(4),
							value instanceof MediaModel && ((MediaModel) value).getMediaContainer() == null ? value : null);
				}
				break;
		}
	}

	protected void resumo(final ProductModel product, final String text, final Locale locale)
	{
		final String antes = product.getSummary(locale);
		final String descricao = product.getDescription(locale);
		product.setSummary(text, locale);
		if (StringUtils.isBlank(descricao) || StringUtils.equals(descricao, antes))
		{
			product.setDescription(text, locale);
		}
	}

	protected Object pendente(final ProductModel product, final String attribute)
	{
		try
		{
			return modelService.getAttributeValue(product, attribute);
		}
		catch (final RuntimeException e)
		{
			return null;
		}
	}

	protected static String texto(final Object value)
	{
		return value == null ? null : StringUtils.trimToNull(value.toString());
	}

	public void setProdutoCadastroService(final ProdutoCadastroService produtoCadastroService)
	{
		this.produtoCadastroService = produtoCadastroService;
	}

	public void setProductImageService(final ProductImageService productImageService)
	{
		this.productImageService = productImageService;
	}

	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}

	public void setCampo(final String campo)
	{
		this.campo = campo;
	}
}
