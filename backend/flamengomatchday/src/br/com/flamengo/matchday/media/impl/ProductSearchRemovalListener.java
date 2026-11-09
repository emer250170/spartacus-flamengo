package br.com.flamengo.matchday.media.impl;

import de.hybris.platform.core.PK;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.jalo.type.ComposedType;
import de.hybris.platform.jalo.type.TypeManager;
import de.hybris.platform.tx.AfterSaveEvent;
import de.hybris.platform.tx.AfterSaveListener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Produto apagado sai da busca da loja (08/11).
 * <p>
 * O índice da loja (electronicsIndex) só é refeito por inteiro de madrugada; a atualização de minuto em minuto pega
 * produtos alterados, não os apagados. Produto apagado durante o dia (Backoffice, Claude, HAC) continuava na
 * categoria e na busca, e a loja mostrava "Product with code ... not found!" ao montar a listagem. O Backoffice já
 * tira o produto da busca dele; esta classe faz o mesmo para a loja.
 * <p>
 * O Commerce chama {@link #afterSave(Collection)} depois de gravar cada transação (inclusive exclusões). Aqui só se
 * separam as exclusões de produto (Product e subtipos, pelo código de tipo da PK) e se agenda a retirada no
 * {@link ProductSearchIndexRefresher}, numa linha de execução própria: nada de Solr na transação de quem apagou.
 * Chave {@code flamengo.produtos.busca.remover} (padrão true) no refresher.
 */
public class ProductSearchRemovalListener implements AfterSaveListener
{
	private static final Logger LOG = LoggerFactory.getLogger(ProductSearchRemovalListener.class);

	/** código de tipo da PK → é Product (ou subtipo)? */
	private final Map<Integer, Boolean> productTypeCodes = new ConcurrentHashMap<>();

	private ProductSearchIndexRefresher searchIndexRefresher;

	@Override
	public void afterSave(final Collection<AfterSaveEvent> events)
	{
		if (events == null || events.isEmpty())
		{
			return;
		}
		List<PK> removed = null;
		for (final AfterSaveEvent event : events)
		{
			if (event.getType() == AfterSaveEvent.REMOVE && event.getPk() != null && isProduct(event.getPk().getTypeCode()))
			{
				if (removed == null)
				{
					removed = new ArrayList<>();
				}
				removed.add(event.getPk());
			}
		}
		if (removed != null)
		{
			try
			{
				searchIndexRefresher.removeLater(removed);
			}
			catch (final RuntimeException e)
			{
				LOG.warn("Não foi possível agendar a retirada da busca dos produtos apagados {}: {}", removed, e.toString());
			}
		}
	}

	protected boolean isProduct(final int typeCode)
	{
		final Integer code = Integer.valueOf(typeCode);
		final Boolean known = productTypeCodes.get(code);
		if (known != null)
		{
			return known.booleanValue();
		}
		try
		{
			final TypeManager typeManager = TypeManager.getInstance();
			final ComposedType type = typeManager.getRootComposedType(typeCode);
			final boolean product = type != null && typeManager.getComposedType(ProductModel._TYPECODE).isAssignableFrom(type);
			productTypeCodes.put(code, Boolean.valueOf(product));
			return product;
		}
		catch (final RuntimeException e)
		{
			// tipo ainda não carregado (subida do servidor) ou desconhecido: não guarda, tenta de novo na próxima
			LOG.debug("tipo {} desconhecido", code, e);
			return false;
		}
	}

	public void setSearchIndexRefresher(final ProductSearchIndexRefresher searchIndexRefresher)
	{
		this.searchIndexRefresher = searchIndexRefresher;
	}
}
