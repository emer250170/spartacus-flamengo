package br.com.flamengo.matchday.occ;

import de.hybris.platform.commerceservices.enums.PickupInStoreMode;
import de.hybris.platform.core.model.c2l.CurrencyModel;
import de.hybris.platform.deliveryzone.model.ZoneDeliveryModeModel;
import de.hybris.platform.deliveryzone.model.ZoneDeliveryModeValueModel;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.i18n.CommonI18NService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;


/**
 * "Calcule o frete" da página do produto (06/11): GET /{baseSiteId}/fladelivery/quote?postalCode=...
 * devolve as formas de entrega da loja para o Brasil com o preço cadastrado (zona com o Brasil,
 * moeda da sessão) e se há retirada grátis na loja. Os modos de entrega digitais (ingresso, sem
 * frete) ficam de fora: flamengo.frete.simulacao.ignorar. Chave flamengo.frete.simulacao.ativo.
 */
@Controller
@RequestMapping(value = "/{baseSiteId}/fladelivery")
public class DeliveryQuoteController
{
	private static final Pattern CEP = Pattern.compile("^[0-9]{8}$");

	private FlexibleSearchService flexibleSearchService;
	private BaseStoreService baseStoreService;
	private CommonI18NService commonI18NService;
	private ConfigurationService configurationService;

	@RequestMapping(value = "/quote", method = RequestMethod.GET)
	@ResponseBody
	public ResponseEntity<Object> quote(@PathVariable final String baseSiteId,
			@RequestParam(value = "postalCode", required = false) final String postalCode)
	{
		final Map<String, Object> body = new LinkedHashMap<>();
		final String cep = StringUtils.defaultString(postalCode).replaceAll("\\D", "");
		if (!configurationService.getConfiguration().getBoolean("flamengo.frete.simulacao.ativo", true))
		{
			body.put("options", new ArrayList<>());
			return ResponseEntity.ok(body);
		}
		if (!CEP.matcher(cep).matches())
		{
			final Map<String, String> error = new LinkedHashMap<>();
			error.put("type", "DeliveryQuoteError");
			error.put("message", "Informe um CEP com 8 números.");
			body.put("errors", java.util.Collections.singletonList(error));
			return ResponseEntity.badRequest().body(body);
		}
		final BaseStoreModel store = baseStoreService.getCurrentBaseStore();
		final CurrencyModel currency = commonI18NService.getCurrentCurrency();
		final Set<String> ignore = new java.util.HashSet<>(java.util.Arrays.asList(StringUtils
				.defaultString(configurationService.getConfiguration().getString("flamengo.frete.simulacao.ignorar", "matchday-digital"))
				.split("\\s*,\\s*")));
		final FlexibleSearchQuery q = new FlexibleSearchQuery("SELECT {pk} FROM {ZoneDeliveryModeValue} WHERE {currency}=?cur");
		q.addQueryParameter("cur", currency);
		final List<ZoneDeliveryModeValueModel> values = flexibleSearchService.<ZoneDeliveryModeValueModel> search(q).getResult();
		final Map<String, Map<String, Object>> byMode = new LinkedHashMap<>();
		for (final ZoneDeliveryModeValueModel v : values)
		{
			final ZoneDeliveryModeModel mode = v.getDeliveryMode();
			if (mode == null || !Boolean.TRUE.equals(mode.getActive()) || !inBrazil(v) || ignore.contains(mode.getCode())
					|| (store != null && store.getDeliveryModes() != null && !store.getDeliveryModes().contains(mode))
					|| (v.getMinimum() != null && v.getMinimum().doubleValue() > 0))
			{
				continue;
			}
			// bruto e líquido com o mesmo nome e preço: fica um só
			final String key = StringUtils.defaultString(mode.getName()) + "|" + v.getValue();
			if (byMode.containsKey(key))
			{
				continue;
			}
			final Map<String, Object> m = new LinkedHashMap<>();
			m.put("code", mode.getCode());
			m.put("name", mode.getName());
			m.put("description", mode.getDescription());
			m.put("price", v.getValue());
			byMode.put(key, m);
		}
		final List<Map<String, Object>> options = byMode.values().stream()
				.sorted(Comparator.comparingDouble(m -> ((Number) m.get("price")).doubleValue())).collect(Collectors.toList());
		body.put("postalCode", cep);
		body.put("options", options);
		body.put("pickup", Boolean.valueOf(store != null && store.getPickupInStoreMode() != null
				&& store.getPickupInStoreMode() != PickupInStoreMode.DISABLED
				&& configurationService.getConfiguration().getBoolean("flamengo.retirada.ativo", true)));
		return ResponseEntity.ok(body);
	}

	/** A zona do preço inclui o Brasil */
	protected static boolean inBrazil(final ZoneDeliveryModeValueModel v)
	{
		return v.getZone() != null && v.getZone().getCountries() != null
				&& v.getZone().getCountries().stream().anyMatch(c -> "BR".equals(c.getIsocode()));
	}

	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}

	public void setBaseStoreService(final BaseStoreService baseStoreService)
	{
		this.baseStoreService = baseStoreService;
	}

	public void setCommonI18NService(final CommonI18NService commonI18NService)
	{
		this.commonI18NService = commonI18NService;
	}

	public void setConfigurationService(final ConfigurationService configurationService)
	{
		this.configurationService = configurationService;
	}
}
