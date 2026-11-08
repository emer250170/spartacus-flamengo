package br.com.flamengo.matchday.estudio.impl;

import br.com.flamengo.matchday.estudio.MengoEstudioCartService;
import br.com.flamengo.matchday.estudio.MengoEstudioService;
import de.hybris.platform.basecommerce.model.site.BaseSiteModel;
import de.hybris.platform.commerceservices.order.CommerceCartModification;
import de.hybris.platform.commerceservices.order.CommerceCartModificationException;
import de.hybris.platform.commerceservices.order.CommerceCartService;
import de.hybris.platform.commerceservices.service.data.CommerceCartParameter;
import de.hybris.platform.core.model.ItemModel;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.order.CartService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.site.BaseSiteService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Mengo Estúdio no carrinho (08/11, PR 3). Os atributos novos (mengoEstampa, mengoCor, mengoTamanho na linha;
 * mengoModelo no produto) são lidos e gravados sem depender das classes geradas, como no resto do Estúdio.
 */
public class DefaultMengoEstudioCartService implements MengoEstudioCartService
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultMengoEstudioCartService.class);
	private static final List<String> CORES = java.util.Arrays.asList("PRT", "BRA", "CIN");
	private static final String SAIU = "Esta estampa saiu de linha e não pode mais ser comprada.";

	private MengoEstudioService mengoEstudioService;
	private CommerceCartService commerceCartService;
	private CartService cartService;
	private BaseSiteService baseSiteService;
	private ModelService modelService;
	private FlexibleSearchService flexibleSearchService;

	@Override
	public boolean isCamiseta(final ProductModel product)
	{
		return product != null && str(product, "mengoModelo") != null;
	}

	@Override
	public boolean isEstudioEntry(final AbstractOrderEntryModel entry)
	{
		return entry != null && attr(entry, "mengoEstampa") instanceof ItemModel;
	}

	@Override
	public int quantidadeMax()
	{
		return (int) Math.max(1, mengoEstudioService instanceof DefaultMengoEstudioService
				? ((DefaultMengoEstudioService) mengoEstudioService).cfgLong("flamengo.estudio.quantidade.max", 10)
				: 10);
	}

	@Override
	public CartModel carrinho(final CustomerModel owner, final String cartId)
	{
		if (owner == null)
		{
			throw new IllegalArgumentException("Entre na sua conta para comprar no Mengo Estúdio.");
		}
		if (StringUtils.isBlank(cartId) || "current".equals(cartId))
		{
			final BaseSiteModel site = baseSiteService.getCurrentBaseSite();
			CartModel cart = site == null ? null : commerceCartService.getCartForGuidAndSiteAndUser(null, site, owner);
			if (cart == null)
			{
				cart = cartService.getSessionCart();
				if (!owner.equals(cart.getUser()))
				{
					cart.setUser(owner);
				}
				modelService.save(cart);
				LOG.info("Mengo Estúdio: carrinho {} criado para {}", cart.getCode(), owner.getUid());
			}
			return cart;
		}
		final FlexibleSearchQuery q = new FlexibleSearchQuery("SELECT {pk} FROM {Cart} WHERE {code}=?c AND {user}=?u");
		q.addQueryParameter("c", cartId.trim());
		q.addQueryParameter("u", owner);
		final List<CartModel> carts = flexibleSearchService.<CartModel> search(q).getResult();
		if (carts.isEmpty())
		{
			throw new IllegalArgumentException("Carrinho não encontrado.");
		}
		return carts.get(0);
	}

	@Override
	public Map<String, Object> adicionar(final CartModel cart, final String estampaCode, final String camisetaCode,
			final String cor, final String tamanho, final int quantidade, final Integer substituir)
	{
		if (!mengoEstudioService.isActive())
		{
			throw new IllegalArgumentException("O Mengo Estúdio não está disponível agora.");
		}
		final ItemModel estampa = StringUtils.isBlank(estampaCode) ? null : mengoEstudioService.estampa(estampaCode.trim());
		if (estampa == null || !mengoEstudioService.isVisible(estampa))
		{
			throw new IllegalArgumentException("Escolha uma estampa.");
		}
		if (!mengoEstudioService.onSale(estampa))
		{
			throw new IllegalArgumentException(SAIU);
		}
		final ProductModel camiseta = mengoEstudioService.camisetas().stream()
				.filter(p -> StringUtils.equals(p.getCode(), StringUtils.trimToEmpty(camisetaCode))).findFirst().orElse(null);
		if (camiseta == null)
		{
			throw new IllegalArgumentException("Escolha o modelo e o tecido da camiseta.");
		}
		final String c = StringUtils.upperCase(StringUtils.trimToEmpty(cor));
		if (!CORES.contains(c))
		{
			throw new IllegalArgumentException("Escolha a cor.");
		}
		final Map<String, String> bloqueadas = mengoEstudioService.blockedColors(estampa);
		if (bloqueadas.containsKey(c))
		{
			final String motivo = bloqueadas.get(c);
			throw new IllegalArgumentException(StringUtils.isBlank(motivo) ? "Esta cor não está disponível para esta estampa."
					: "Esta cor não está disponível para esta estampa: " + motivo);
		}
		final String t = StringUtils.trimToEmpty(tamanho);
		if (t.isEmpty())
		{
			throw new IllegalArgumentException("Escolha o tamanho.");
		}
		if (!mengoEstudioService.tamanhos(camiseta).contains(t))
		{
			throw new IllegalArgumentException("Este tamanho não existe para o modelo e o tecido escolhidos.");
		}
		final int max = quantidadeMax();
		if (quantidade < 1 || quantidade > max)
		{
			throw new IllegalArgumentException("Escolha de 1 a " + max + " unidades.");
		}

		// linha que está sendo editada: sai depois que a nova configuração foi aceita
		final AbstractOrderEntryModel editada = substituir == null ? null : entry(cart, substituir.intValue());
		if (substituir != null && (editada == null || !isEstudioEntry(editada)))
		{
			throw new IllegalArgumentException("O item que você estava editando não está mais no carrinho.");
		}

		try
		{
			final AbstractOrderEntryModel igual = cart.getEntries() == null ? null
					: cart.getEntries().stream()
							.filter(e -> e != editada && isEstudioEntry(e) && camiseta.equals(e.getProduct())
									&& estampa.equals(attr(e, "mengoEstampa")) && c.equals(str(e, "mengoCor"))
									&& t.equals(str(e, "mengoTamanho")))
							.findFirst().orElse(null);
			int entryNumber;
			long qtd;
			if (igual != null)
			{
				final long atual = igual.getQuantity() == null ? 0 : igual.getQuantity().longValue();
				if (atual >= max)
				{
					throw new IllegalArgumentException("Esta camiseta já está no carrinho com o máximo de " + max + " unidades.");
				}
				qtd = Math.min(max, atual + quantidade);
				entryNumber = igual.getEntryNumber().intValue();
				update(cart, entryNumber, qtd);
			}
			else
			{
				final CommerceCartParameter p = new CommerceCartParameter();
				p.setEnableHooks(true);
				p.setCart(cart);
				p.setProduct(camiseta);
				p.setQuantity(quantidade);
				p.setUnit(camiseta.getUnit());
				p.setCreateNewEntry(true);
				final CommerceCartModification result = commerceCartService.addToCart(p);
				final AbstractOrderEntryModel added = result == null ? null : result.getEntry();
				if (added == null || result.getQuantityAdded() <= 0)
				{
					throw new IllegalArgumentException("Não deu para incluir a camiseta agora. Tente de novo.");
				}
				modelService.setAttributeValue(added, "mengoEstampa", estampa);
				modelService.setAttributeValue(added, "mengoCor", c);
				modelService.setAttributeValue(added, "mengoTamanho", t);
				modelService.save(added);
				entryNumber = added.getEntryNumber().intValue();
				qtd = added.getQuantity() == null ? quantidade : added.getQuantity().longValue();
			}
			if (editada != null)
			{
				update(cart, editada.getEntryNumber().intValue(), 0);
			}
			LOG.info("Mengo Estúdio: carrinho {} linha {} = {} {} {} {} x{}", cart.getCode(), Integer.valueOf(entryNumber),
					camiseta.getCode(), str(estampa, "code"), c, t, Long.valueOf(qtd));
			final Map<String, Object> out = new LinkedHashMap<>();
			out.put("cartCode", cart.getCode());
			out.put("entryNumber", Integer.valueOf(entryNumber));
			out.put("quantidade", Long.valueOf(qtd));
			return out;
		}
		catch (final CommerceCartModificationException e)
		{
			LOG.warn("Mengo Estúdio: inclusão recusada no carrinho {}: {}", cart.getCode(), e.getMessage());
			throw new IllegalArgumentException(StringUtils.defaultIfBlank(e.getMessage(), "Não deu para incluir a camiseta agora."));
		}
	}

	protected void update(final CartModel cart, final int entryNumber, final long qty) throws CommerceCartModificationException
	{
		final CommerceCartParameter p = new CommerceCartParameter();
		p.setEnableHooks(true);
		p.setCart(cart);
		p.setEntryNumber(entryNumber);
		p.setQuantity(qty);
		commerceCartService.updateQuantityForCartEntry(p);
	}

	@Override
	public List<Map<String, Object>> itens(final AbstractOrderModel order)
	{
		if (order == null || order.getEntries() == null)
		{
			return Collections.emptyList();
		}
		final long now = mengoEstudioService.now();
		final List<Map<String, Object>> list = new ArrayList<>();
		for (final AbstractOrderEntryModel e : order.getEntries())
		{
			if (!isEstudioEntry(e))
			{
				continue;
			}
			final ItemModel estampa = (ItemModel) attr(e, "mengoEstampa");
			final ProductModel p = e.getProduct();
			final Map<String, Object> m = new LinkedHashMap<>();
			m.put("entryNumber", e.getEntryNumber());
			m.put("estampa", mengoEstudioService.estampaDto(estampa, now));
			m.put("camiseta", p == null ? null : p.getCode());
			m.put("modelo", p == null ? null : str(p, "mengoModelo"));
			m.put("tecido", p == null ? null : str(p, "mengoTecido"));
			m.put("cor", str(e, "mengoCor"));
			m.put("tamanho", str(e, "mengoTamanho"));
			m.put("quantidade", e.getQuantity());
			m.put("encerrada", Boolean.valueOf(!mengoEstudioService.onSale(estampa)));
			m.put("numeroSerie", str(e, "mengoNumeroSerie"));
			list.add(m);
		}
		return list;
	}

	@Override
	public String bloqueio(final AbstractOrderModel cart)
	{
		if (cart == null || cart.getEntries() == null || !mengoEstudioService.isActive())
		{
			return null;
		}
		for (final AbstractOrderEntryModel e : cart.getEntries())
		{
			if (isEstudioEntry(e) && !mengoEstudioService.onSale((ItemModel) attr(e, "mengoEstampa")))
			{
				return "Remova a estampa que saiu de linha para continuar: "
						+ StringUtils.defaultString(str(attr(e, "mengoEstampa"), "name"), "camiseta do Mengo Estúdio") + ".";
			}
		}
		return null;
	}

	protected AbstractOrderEntryModel entry(final AbstractOrderModel order, final int number)
	{
		return order.getEntries() == null ? null
				: order.getEntries().stream()
						.filter(e -> e.getEntryNumber() != null && e.getEntryNumber().intValue() == number).findFirst()
						.orElse(null);
	}

	protected String str(final Object item, final String qualifier)
	{
		final Object v = attr(item, qualifier);
		return v instanceof String && StringUtils.isNotBlank((String) v) ? (String) v : null;
	}

	protected Object attr(final Object item, final String qualifier)
	{
		if (item == null)
		{
			return null;
		}
		try
		{
			return modelService.getAttributeValue(item, qualifier);
		}
		catch (final RuntimeException e)
		{
			return null;
		}
	}

	public void setMengoEstudioService(final MengoEstudioService mengoEstudioService)
	{
		this.mengoEstudioService = mengoEstudioService;
	}

	public void setCommerceCartService(final CommerceCartService commerceCartService)
	{
		this.commerceCartService = commerceCartService;
	}

	public void setCartService(final CartService cartService)
	{
		this.cartService = cartService;
	}

	public void setBaseSiteService(final BaseSiteService baseSiteService)
	{
		this.baseSiteService = baseSiteService;
	}

	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}

	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}
}
