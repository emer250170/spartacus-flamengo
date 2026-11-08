package br.com.flamengo.matchday.loja;

import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.servicelayer.interceptor.InitDefaultsInterceptor;
import de.hybris.platform.servicelayer.interceptor.InterceptorContext;
import de.hybris.platform.servicelayer.interceptor.InterceptorException;
import de.hybris.platform.servicelayer.interceptor.PrepareInterceptor;

import java.util.ArrayList;
import java.util.List;


/**
 * Produto do cadastro simples da loja (08/11):
 * <ul>
 * <li>novo, criado pelo gestor da loja: já nasce no catálogo da loja (Online), aprovado e com unidade;</li>
 * <li>gravado com preço, estoque ou foto pendente (assistente "Novo produto" ou aba "Cadastro"): confere os valores e,
 * logo depois da gravação, aplica o preço, o estoque e as fotos ({@link ProdutoCadastroService#aplicarDepoisDeGravar}).</li>
 * </ul>
 * Produto sem pendente (importação, jogos, experiências, Mengo Estúdio) não muda. Mapeado pelo código do tipo
 * (Product); os atributos novos são lidos pelo nome.
 */
public class ProdutoCadastroInterceptor implements InitDefaultsInterceptor<ProductModel>, PrepareInterceptor<ProductModel>
{
	private ProdutoCadastroService produtoCadastroService;

	@Override
	public void onInitDefaults(final ProductModel product, final InterceptorContext ctx) throws InterceptorException
	{
		if (produtoCadastroService.ativo() && produtoCadastroService.usuarioDoCadastro())
		{
			produtoCadastroService.valoresIniciais(product);
		}
	}

	@Override
	public void onPrepare(final ProductModel product, final InterceptorContext ctx) throws InterceptorException
	{
		if (!produtoCadastroService.ativo())
		{
			return;
		}
		final List<String> pendentes = new ArrayList<>();
		pendentes.add(ProdutoCadastroService.PRECO_NOVO);
		pendentes.add(ProdutoCadastroService.ESTOQUE_NOVO);
		for (int i = 0; i < 4; i++)
		{
			pendentes.add(ProdutoCadastroService.FOTO_NOVA + i);
		}
		boolean mudou = false;
		for (final String attribute : pendentes)
		{
			if ((ctx.isNew(product) || ctx.isModified(product, attribute))
					&& ctx.getModelService().getAttributeValue(product, attribute) != null)
			{
				mudou = true;
			}
		}
		if (!mudou)
		{
			return;
		}
		final Object preco = ctx.getModelService().getAttributeValue(product, ProdutoCadastroService.PRECO_NOVO);
		if (preco instanceof Number && ((Number) preco).doubleValue() < 0)
		{
			throw new InterceptorException("O preço não pode ser negativo.");
		}
		final Object estoque = ctx.getModelService().getAttributeValue(product, ProdutoCadastroService.ESTOQUE_NOVO);
		if (estoque instanceof Number && ((Number) estoque).intValue() < 0)
		{
			throw new InterceptorException("O estoque não pode ser negativo.");
		}
		produtoCadastroService.aplicarDepoisDeGravar(product);
	}

	public void setProdutoCadastroService(final ProdutoCadastroService produtoCadastroService)
	{
		this.produtoCadastroService = produtoCadastroService;
	}
}
