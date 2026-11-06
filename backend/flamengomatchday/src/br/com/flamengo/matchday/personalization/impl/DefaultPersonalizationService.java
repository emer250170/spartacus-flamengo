package br.com.flamengo.matchday.personalization.impl;

import br.com.flamengo.matchday.personalization.Personalization;
import br.com.flamengo.matchday.personalization.PersonalizationException;
import br.com.flamengo.matchday.personalization.PersonalizationOption;
import br.com.flamengo.matchday.personalization.PersonalizationService;
import de.hybris.platform.category.CategoryService;
import de.hybris.platform.category.model.CategoryModel;
import de.hybris.platform.commerceservices.order.CommerceCartCalculationStrategy;
import de.hybris.platform.commerceservices.price.CommercePriceService;
import de.hybris.platform.commerceservices.service.data.CommerceCartParameter;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.order.CartEntryModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.jalo.order.price.PriceInformation;
import de.hybris.platform.order.CartService;
import de.hybris.platform.product.ProductService;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.variants.model.VariantProductModel;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/** Ver {@link PersonalizationService}. */
public class DefaultPersonalizationService implements PersonalizationService
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultPersonalizationService.class);

	/** Até 13 letras e espaços (sem acento depois de normalizar, como na fonte oficial) */
	private static final Pattern NAME = Pattern.compile("^[A-Z ]{1,13}$");
	private static final Pattern NUMBER = Pattern.compile("^[0-9]{1,2}$");

	private ConfigurationService configurationService;
	private ModelService modelService;
	private FlexibleSearchService flexibleSearchService;
	private ProductService productService;
	private CategoryService categoryService;
	private CartService cartService;
	private CommerceCartCalculationStrategy commerceCartCalculationStrategy;
	private CommercePriceService commercePriceService;

	@Override
	public boolean isActive()
	{
		return configurationService.getConfiguration().getBoolean(ACTIVE, false);
	}

	@Override
	public boolean isPersonalizable(final String productCode)
	{
		if (!isActive() || StringUtils.isBlank(productCode))
		{
			return false;
		}
		final ProductModel product = product(productCode);
		return product != null && isPersonalizable(product);
	}

	protected boolean isPersonalizable(final ProductModel product)
	{
		final Set<String> wanted = csv(CATEGORIES);
		if (wanted.isEmpty())
		{
			return false;
		}
		final List<ProductModel> products = new ArrayList<>();
		products.add(product);
		if (product instanceof VariantProductModel && ((VariantProductModel) product).getBaseProduct() != null)
		{
			products.add(((VariantProductModel) product).getBaseProduct());
		}
		for (final ProductModel p : products)
		{
			for (final CategoryModel c : nullSafe(p.getSupercategories()))
			{
				if (wanted.contains(c.getCode()))
				{
					return true;
				}
				for (final CategoryModel sup : nullSafe(categoryService.getAllSupercategoriesForCategory(c)))
				{
					if (wanted.contains(sup.getCode()))
					{
						return true;
					}
				}
			}
		}
		return false;
	}

	@Override
	public PersonalizationOption nameNumberOption()
	{
		return option(config(NAME_NUMBER));
	}

	@Override
	public PersonalizationOption sponsorOption()
	{
		return option(config(SPONSOR));
	}

	@Override
	public List<PersonalizationOption> patchOptions()
	{
		final List<PersonalizationOption> out = new ArrayList<>();
		for (final String code : csv(PATCHES))
		{
			final PersonalizationOption o = option(code);
			if (o != null)
			{
				out.add(o);
			}
		}
		return out;
	}

	@Override
	public List<Personalization> list(final CustomerModel owner, final String cartCode) throws PersonalizationException
	{
		return of(cart(owner, cartCode));
	}

	@Override
	public List<Personalization> listOrder(final CustomerModel owner, final String orderCode) throws PersonalizationException
	{
		final FlexibleSearchQuery q = new FlexibleSearchQuery(
				"SELECT {pk} FROM {Order} WHERE {code}=?c AND {user}=?u AND {versionID} IS NULL");
		q.addQueryParameter("c", StringUtils.trimToEmpty(orderCode));
		q.addQueryParameter("u", owner);
		final List<OrderModel> orders = flexibleSearchService.<OrderModel> search(q).getResult();
		if (orders.isEmpty())
		{
			throw new PersonalizationException("Pedido não encontrado.");
		}
		return of(orders.get(0));
	}

	@Override
	public List<Personalization> set(final CustomerModel owner, final String cartCode, final String productCode,
			final String name, final String number, final boolean sponsor, final String patchCode)
			throws PersonalizationException
	{
		if (!isActive())
		{
			throw new PersonalizationException("A personalização não está disponível.");
		}
		final CartModel cart = cart(owner, cartCode);
		final AbstractOrderEntryModel entry = cart.getEntries().stream()
				.filter(e -> e.getProduct() != null && e.getProduct().getCode().equals(productCode) && str(e, ENTRY_OF) == null)
				.findFirst().orElse(null);
		if (entry == null)
		{
			throw new PersonalizationException("O produto " + StringUtils.defaultString(productCode) + " não está no seu carrinho.");
		}
		if (!isPersonalizable(entry.getProduct()))
		{
			throw new PersonalizationException("Este produto não pode ser personalizado.");
		}
		final String cleanName = cleanName(name);
		final String cleanNumber = StringUtils.trimToNull(number);
		if (cleanName != null && !NAME.matcher(cleanName).matches())
		{
			throw new PersonalizationException("Nome: até 13 letras, sem números nem caracteres especiais.");
		}
		if (cleanNumber != null && !NUMBER.matcher(cleanNumber).matches())
		{
			throw new PersonalizationException("Número: de 0 a 99.");
		}
		final String patch = StringUtils.trimToNull(patchCode);
		if (patch != null && !csv(PATCHES).contains(patch))
		{
			throw new PersonalizationException("Patch não disponível.");
		}
		if (sponsor && option(config(SPONSOR)) == null)
		{
			throw new PersonalizationException("Kit de patrocínio não disponível.");
		}
		if ((cleanName != null || cleanNumber != null) && option(config(NAME_NUMBER)) == null)
		{
			throw new PersonalizationException("Nome e número não disponíveis.");
		}
		modelService.setAttributeValue(entry, ENTRY_NAME, cleanName);
		modelService.setAttributeValue(entry, ENTRY_NUMBER, cleanNumber);
		modelService.setAttributeValue(entry, ENTRY_SPONSOR, sponsor ? Boolean.TRUE : null);
		modelService.setAttributeValue(entry, ENTRY_PATCH, patch);
		modelService.save(entry);
		LOG.info("Personalização no carrinho {}: {} nome={} número={} patrocínio={} patch={}", cart.getCode(), productCode,
				cleanName, cleanNumber, Boolean.valueOf(sponsor), patch);
		syncAddOns(cart);
		return of(cart);
	}

	@Override
	public boolean syncAddOns(final CartModel cart)
	{
		if (cart == null || cart.getEntries() == null)
		{
			return false;
		}
		// o que cada manto personalizado pede: código do manto -> (produto de preço -> quantidade)
		final Map<String, Map<String, Long>> wanted = new LinkedHashMap<>();
		final List<AbstractOrderEntryModel> addOns = new ArrayList<>();
		for (final AbstractOrderEntryModel e : cart.getEntries())
		{
			if (modelService.isRemoved(e) || e.getProduct() == null)
			{
				continue;
			}
			if (str(e, ENTRY_OF) != null)
			{
				addOns.add(e);
				continue;
			}
			final Map<String, Long> want = wantedAddOns(e);
			if (!want.isEmpty())
			{
				wanted.put(e.getProduct().getCode(), want);
			}
		}
		boolean changed = false;
		final Set<String> done = new HashSet<>();
		for (final AbstractOrderEntryModel a : addOns)
		{
			final String of = str(a, ENTRY_OF);
			final String code = a.getProduct().getCode();
			final Long qty = wanted.containsKey(of) ? wanted.get(of).get(code) : null;
			if (qty == null || !done.add(of + "|" + code))
			{
				modelService.remove(a);
				changed = true;
			}
			else if (a.getQuantity() == null || a.getQuantity().longValue() != qty.longValue())
			{
				a.setQuantity(qty);
				a.setCalculated(Boolean.FALSE);
				modelService.save(a);
				changed = true;
			}
		}
		for (final Map.Entry<String, Map<String, Long>> w : wanted.entrySet())
		{
			for (final Map.Entry<String, Long> item : w.getValue().entrySet())
			{
				if (done.contains(w.getKey() + "|" + item.getKey()))
				{
					continue;
				}
				final ProductModel product = product(item.getKey());
				if (product == null)
				{
					LOG.warn("Produto de preço da personalização {} não encontrado", item.getKey());
					continue;
				}
				final CartEntryModel added = cartService.addNewEntry(cart, product, item.getValue().longValue(), product.getUnit(),
						-1, false);
				modelService.setAttributeValue(added, ENTRY_OF, w.getKey());
				modelService.save(added);
				changed = true;
			}
		}
		if (changed)
		{
			modelService.save(cart);
			modelService.refresh(cart);
			final CommerceCartParameter parameter = new CommerceCartParameter();
			parameter.setEnableHooks(true);
			parameter.setCart(cart);
			commerceCartCalculationStrategy.recalculateCart(parameter);
			LOG.info("Personalização: linhas de preço do carrinho {} ajustadas", cart.getCode());
		}
		return changed;
	}

	@Override
	public void dropOption(final CartModel cart, final int addOnEntryNumber)
	{
		if (cart == null || cart.getEntries() == null)
		{
			return;
		}
		final AbstractOrderEntryModel addOn = cart.getEntries().stream()
				.filter(e -> e.getEntryNumber() != null && e.getEntryNumber().intValue() == addOnEntryNumber).findFirst()
				.orElse(null);
		final String of = addOn == null ? null : str(addOn, ENTRY_OF);
		if (of == null || addOn.getProduct() == null)
		{
			return;
		}
		final String code = addOn.getProduct().getCode();
		for (final AbstractOrderEntryModel e : cart.getEntries())
		{
			if (e.getProduct() == null || !of.equals(e.getProduct().getCode()) || str(e, ENTRY_OF) != null)
			{
				continue;
			}
			if (code.equals(config(NAME_NUMBER)))
			{
				modelService.setAttributeValue(e, ENTRY_NAME, null);
				modelService.setAttributeValue(e, ENTRY_NUMBER, null);
			}
			else if (code.equals(config(SPONSOR)))
			{
				modelService.setAttributeValue(e, ENTRY_SPONSOR, null);
			}
			else if (code.equals(str(e, ENTRY_PATCH)))
			{
				modelService.setAttributeValue(e, ENTRY_PATCH, null);
			}
			modelService.save(e);
			LOG.info("Personalização: {} saiu do manto {} no carrinho {}", code, of, cart.getCode());
		}
	}

	@Override
	public List<Personalization> of(final AbstractOrderModel order)
	{
		final List<Personalization> out = new ArrayList<>();
		if (order == null || order.getEntries() == null)
		{
			return out;
		}
		final Map<String, Personalization> byProduct = new LinkedHashMap<>();
		for (final AbstractOrderEntryModel e : order.getEntries())
		{
			if (e.getProduct() == null || str(e, ENTRY_OF) != null || wantedAddOns(e).isEmpty())
			{
				continue;
			}
			final Personalization p = new Personalization();
			p.entryNumber = e.getEntryNumber() == null ? -1 : e.getEntryNumber().intValue();
			p.productCode = e.getProduct().getCode();
			p.name = str(e, ENTRY_NAME);
			p.number = str(e, ENTRY_NUMBER);
			p.sponsor = Boolean.TRUE.equals(attr(e, ENTRY_SPONSOR));
			p.patchCode = str(e, ENTRY_PATCH);
			if (p.patchCode != null)
			{
				final ProductModel patch = product(p.patchCode);
				p.patchName = patch == null ? p.patchCode : patch.getName();
			}
			byProduct.putIfAbsent(p.productCode, p);
			out.add(p);
		}
		for (final AbstractOrderEntryModel e : order.getEntries())
		{
			final String of = str(e, ENTRY_OF);
			if (of != null && byProduct.containsKey(of) && e.getEntryNumber() != null)
			{
				byProduct.get(of).addOnEntries.add(e.getEntryNumber());
			}
		}
		return out;
	}

	/** Produtos de preço que a linha do manto pede, com a quantidade do manto */
	protected Map<String, Long> wantedAddOns(final AbstractOrderEntryModel e)
	{
		final Map<String, Long> want = new LinkedHashMap<>();
		final long qty = e.getQuantity() == null ? 0 : e.getQuantity().longValue();
		if (qty <= 0)
		{
			return want;
		}
		if (str(e, ENTRY_NAME) != null || str(e, ENTRY_NUMBER) != null)
		{
			put(want, config(NAME_NUMBER), qty);
		}
		if (Boolean.TRUE.equals(attr(e, ENTRY_SPONSOR)))
		{
			put(want, config(SPONSOR), qty);
		}
		put(want, str(e, ENTRY_PATCH), qty);
		return want;
	}

	protected void put(final Map<String, Long> map, final String code, final long qty)
	{
		if (StringUtils.isNotBlank(code))
		{
			map.put(code, Long.valueOf(qty));
		}
	}

	/** "Émerson  " -> "EMERSON" (sem acento, maiúsculas, espaços normalizados) */
	protected static String cleanName(final String name)
	{
		final String n = StringUtils.trimToNull(StringUtils.normalizeSpace(name));
		if (n == null)
		{
			return null;
		}
		return Normalizer.normalize(n, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT);
	}

	protected PersonalizationOption option(final String code)
	{
		final ProductModel product = StringUtils.isBlank(code) ? null : product(code);
		if (product == null)
		{
			return null;
		}
		final PersonalizationOption o = new PersonalizationOption();
		o.code = product.getCode();
		o.name = product.getName();
		try
		{
			final PriceInformation price = commercePriceService.getWebPriceForProduct(product);
			o.price = price == null || price.getPriceValue() == null ? null : Double.valueOf(price.getPriceValue().getValue());
		}
		catch (final RuntimeException e)
		{
			LOG.warn("Sem preço para {}", code);
		}
		return o;
	}

	protected ProductModel product(final String code)
	{
		try
		{
			return productService.getProductForCode(code);
		}
		catch (final RuntimeException e)
		{
			return null;
		}
	}

	protected CartModel cart(final CustomerModel owner, final String cartCode) throws PersonalizationException
	{
		final FlexibleSearchQuery q = new FlexibleSearchQuery("SELECT {pk} FROM {Cart} WHERE {code}=?c AND {user}=?u");
		q.addQueryParameter("c", StringUtils.trimToEmpty(cartCode));
		q.addQueryParameter("u", owner);
		final List<CartModel> carts = flexibleSearchService.<CartModel> search(q).getResult();
		if (carts.isEmpty())
		{
			throw new PersonalizationException("Carrinho não encontrado.");
		}
		return carts.get(0);
	}

	protected String config(final String key)
	{
		return StringUtils.trimToNull(configurationService.getConfiguration().getString(key, ""));
	}

	protected Set<String> csv(final String key)
	{
		final String value = config(key);
		return value == null ? new HashSet<>()
				: Arrays.stream(value.split(",")).map(String::trim).filter(StringUtils::isNotEmpty)
						.collect(Collectors.toCollection(java.util.LinkedHashSet::new));
	}

	protected String str(final Object item, final String qualifier)
	{
		final Object v = attr(item, qualifier);
		return v instanceof String && StringUtils.isNotBlank((String) v) ? (String) v : null;
	}

	protected Object attr(final Object item, final String qualifier)
	{
		try
		{
			return modelService.getAttributeValue(item, qualifier);
		}
		catch (final RuntimeException e)
		{
			return null;
		}
	}

	protected static <T> Collection<T> nullSafe(final Collection<T> c)
	{
		return c == null ? new ArrayList<>() : c;
	}

	public void setConfigurationService(final ConfigurationService configurationService)
	{
		this.configurationService = configurationService;
	}

	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}

	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}

	public void setProductService(final ProductService productService)
	{
		this.productService = productService;
	}

	public void setCategoryService(final CategoryService categoryService)
	{
		this.categoryService = categoryService;
	}

	public void setCartService(final CartService cartService)
	{
		this.cartService = cartService;
	}

	public void setCommerceCartCalculationStrategy(final CommerceCartCalculationStrategy commerceCartCalculationStrategy)
	{
		this.commerceCartCalculationStrategy = commerceCartCalculationStrategy;
	}

	public void setCommercePriceService(final CommercePriceService commercePriceService)
	{
		this.commercePriceService = commercePriceService;
	}
}
