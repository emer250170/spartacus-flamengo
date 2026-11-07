package br.com.flamengo.matchday.estudio.impl;

import br.com.flamengo.matchday.estudio.MengoEstudioService;
import de.hybris.platform.catalog.CatalogVersionService;
import de.hybris.platform.catalog.model.CatalogVersionModel;
import de.hybris.platform.commerceservices.price.CommercePriceService;
import de.hybris.platform.core.model.ItemModel;
import de.hybris.platform.core.model.media.MediaModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.jalo.order.price.PriceInformation;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Regras do Mengo Estúdio. Lê os tipos novos pelo nome (MengoEstampa, MengoDrop, MengoLinha, MengoDemoConfig)
 * e os atributos com getAttributeValue, como o resto da extensão faz com tipos recentes.
 */
public class DefaultMengoEstudioService implements MengoEstudioService
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultMengoEstudioService.class);
	private static final long HOUR = 3_600_000L;
	private static final long DAY = 24 * HOUR;
	protected static final List<String> MODELOS = Arrays.asList("MAS", "FEM", "INF");
	protected static final List<String> TECIDOS = Arrays.asList("ALG", "POL", "OVS");
	protected static final List<String> CORES = Arrays.asList("PRT", "BRA", "CIN");

	private ConfigurationService configurationService;
	private FlexibleSearchService flexibleSearchService;
	private ModelService modelService;
	private CatalogVersionService catalogVersionService;
	private CommercePriceService commercePriceService;

	@Override
	public boolean isActive()
	{
		return configurationService.getConfiguration().getBoolean("flamengo.estudio.ativo", false);
	}

	@Override
	public long now()
	{
		final ItemModel demo = demoConfig();
		final Object offset = demo == null ? null : attr(demo, "clockOffsetMinutes");
		return System.currentTimeMillis() + (offset instanceof Number ? ((Number) offset).longValue() * 60_000L : 0L);
	}

	@Override
	public List<ItemModel> estampas()
	{
		final List<ItemModel> list = search("SELECT {pk} FROM {MengoEstampa} WHERE {active} = 1 OR {active} IS NULL",
				Collections.emptyMap());
		final List<ItemModel> sorted = new ArrayList<>(list);
		sorted.sort(Comparator.comparingLong(this::end));
		return sorted;
	}

	@Override
	public ItemModel estampa(final String code)
	{
		if (StringUtils.isBlank(code))
		{
			return null;
		}
		final Map<String, Object> p = new LinkedHashMap<>();
		p.put("c", code.trim());
		final List<ItemModel> list = search("SELECT {pk} FROM {MengoEstampa} WHERE {code} = ?c", p);
		return list.isEmpty() ? null : list.get(0);
	}

	@Override
	public boolean isVisible(final ItemModel estampa)
	{
		if (estampa == null || Boolean.FALSE.equals(attr(estampa, "active")))
		{
			return false;
		}
		final Object launch = attr(estampa, "launchDate");
		return !(launch instanceof Date) || ((Date) launch).getTime() <= now();
	}

	@Override
	public long end(final ItemModel estampa)
	{
		final String code = str(estampa, "code");
		final Long forced = forcedEnds().get(code);
		if (forced != null)
		{
			return forced.longValue();
		}
		final Object end = attr(estampa, "endDate");
		return end instanceof Date ? ((Date) end).getTime() : Long.MAX_VALUE;
	}

	@Override
	public String situacao(final ItemModel estampa, final long now)
	{
		final long rest = end(estampa) - now;
		if (rest <= 0)
		{
			return ENCERRADA;
		}
		if (rest <= cfgLong("flamengo.estudio.ultimas.horas", 48) * HOUR)
		{
			return ULTIMAS_48H;
		}
		if (rest <= cfgLong("flamengo.estudio.ultimos.dias", 7) * DAY)
		{
			return ULTIMOS_DIAS;
		}
		final Object launch = attr(estampa, "launchDate");
		if (launch instanceof Date && now >= ((Date) launch).getTime()
				&& now - ((Date) launch).getTime() <= cfgLong("flamengo.estudio.nova.dias", 7) * DAY)
		{
			return NOVA;
		}
		return A_VENDA;
	}

	@Override
	public boolean onSale(final ItemModel estampa)
	{
		if (estampa == null || Boolean.FALSE.equals(attr(estampa, "active")))
		{
			return false;
		}
		final Object launch = attr(estampa, "launchDate");
		final long now = now();
		if (launch instanceof Date && now < ((Date) launch).getTime())
		{
			return false;
		}
		final ItemModel drop = (ItemModel) attr(estampa, "drop");
		if (drop != null && Boolean.FALSE.equals(attr(drop, "active")))
		{
			return false;
		}
		return !ENCERRADA.equals(situacao(estampa, now));
	}

	@Override
	public Map<String, String> blockedColors(final ItemModel estampa)
	{
		final Map<String, String> map = new LinkedHashMap<>();
		final String text = str(estampa, "blockedColors");
		if (text != null)
		{
			for (final String line : text.split("\\r?\\n|\\|"))
			{
				final int eq = line.indexOf('=');
				final String cor = (eq > 0 ? line.substring(0, eq) : line).trim().toUpperCase();
				if (CORES.contains(cor))
				{
					map.put(cor, eq > 0 ? line.substring(eq + 1).trim() : "");
				}
			}
		}
		return map;
	}

	@Override
	public List<ProductModel> camisetas()
	{
		final CatalogVersionModel cv = onlineCatalog();
		if (cv == null)
		{
			return Collections.emptyList();
		}
		final Map<String, Object> p = new LinkedHashMap<>();
		p.put("cv", cv);
		final List<ProductModel> list = new ArrayList<>();
		for (final ItemModel i : search(
				"SELECT {pk} FROM {Product} WHERE {catalogVersion} = ?cv AND {mengoModelo} IS NOT NULL ORDER BY {code}", p))
		{
			list.add((ProductModel) i);
		}
		return list;
	}

	@Override
	public ProductModel camiseta(final String modelo, final String tecido)
	{
		for (final ProductModel p : camisetas())
		{
			if (StringUtils.equals(modelo, str(p, "mengoModelo")) && StringUtils.equals(tecido, str(p, "mengoTecido")))
			{
				return p;
			}
		}
		return null;
	}

	@Override
	public List<String> tamanhos(final String modelo, final String tecido)
	{
		final String value = configurationService.getConfiguration()
				.getString("flamengo.estudio.tamanhos." + modelo + "." + tecido, "");
		final List<String> list = new ArrayList<>();
		for (final String t : value.split(","))
		{
			if (StringUtils.isNotBlank(t))
			{
				list.add(t.trim());
			}
		}
		return list;
	}

	@Override
	public Map<String, int[]> medidas(final String modelo, final String tecido)
	{
		// flamengo.estudio.medidas.MAS.ALG=P:50:70,M:53:72 (largura:comprimento em cm)
		final Map<String, int[]> map = new LinkedHashMap<>();
		final String value = configurationService.getConfiguration()
				.getString("flamengo.estudio.medidas." + modelo + "." + tecido, "");
		for (final String part : value.split(","))
		{
			final String[] f = part.trim().split(":");
			if (f.length == 3)
			{
				try
				{
					map.put(f[0], new int[] { Integer.parseInt(f[1]), Integer.parseInt(f[2]) });
				}
				catch (final NumberFormatException e)
				{
					LOG.warn("Medida inválida em {}.{}: {}", modelo, tecido, part);
				}
			}
		}
		return map;
	}

	@Override
	public Map<String, Object> catalogo()
	{
		final long now = now();
		final Map<String, Object> body = new LinkedHashMap<>();
		body.put("ativo", Boolean.valueOf(isActive()));
		body.put("agora", Long.valueOf(now));
		body.put("fuso", configurationService.getConfiguration().getString("flamengo.estudio.fuso", "America/Sao_Paulo"));
		final Map<String, Object> regras = new LinkedHashMap<>();
		regras.put("ultimasHoras", Long.valueOf(cfgLong("flamengo.estudio.ultimas.horas", 48)));
		regras.put("ultimosDias", Long.valueOf(cfgLong("flamengo.estudio.ultimos.dias", 7)));
		regras.put("novaDias", Long.valueOf(cfgLong("flamengo.estudio.nova.dias", 7)));
		regras.put("parcelasMax", Long.valueOf(cfgLong("flamengo.estudio.parcelas.max", 10)));
		regras.put("parcelaMinima", Double.valueOf(configurationService.getConfiguration()
				.getDouble("flamengo.estudio.parcelas.minima", 30d)));
		regras.put("quantidadeMax", Long.valueOf(cfgLong("flamengo.estudio.quantidade.max", 10)));
		body.put("regras", regras);

		final List<Map<String, Object>> drops = new ArrayList<>();
		for (final ItemModel d : search("SELECT {pk} FROM {MengoDrop} WHERE {active} = 1 ORDER BY {number}",
				Collections.emptyMap()))
		{
			final Map<String, Object> m = new LinkedHashMap<>();
			m.put("code", str(d, "code"));
			m.put("numero", attr(d, "number"));
			m.put("nome", str(d, "name"));
			m.put("descricao", str(d, "description"));
			drops.add(m);
		}
		body.put("drops", drops);

		final List<Map<String, Object>> linhas = new ArrayList<>();
		for (final ItemModel l : search("SELECT {pk} FROM {MengoLinha} ORDER BY {position}", Collections.emptyMap()))
		{
			final Map<String, Object> m = new LinkedHashMap<>();
			m.put("code", str(l, "code"));
			m.put("nome", str(l, "name"));
			m.put("descricao", str(l, "description"));
			linhas.add(m);
		}
		body.put("linhas", linhas);

		final List<Map<String, Object>> estampas = new ArrayList<>();
		for (final ItemModel e : estampas())
		{
			final ItemModel drop = (ItemModel) attr(e, "drop");
			final Object launch = attr(e, "launchDate");
			// estampa ainda não lançada não aparece; a de drop desligado também não (exceto no acervo)
			if (launch instanceof Date && ((Date) launch).getTime() > now)
			{
				continue;
			}
			if (drop != null && Boolean.FALSE.equals(attr(drop, "active")) && !ENCERRADA.equals(situacao(e, now)))
			{
				continue;
			}
			estampas.add(estampaDto(e, now));
		}
		body.put("estampas", estampas);

		final List<Map<String, Object>> camisetas = new ArrayList<>();
		for (final ProductModel p : camisetas())
		{
			final String modelo = str(p, "mengoModelo");
			final String tecido = str(p, "mengoTecido");
			final List<String> tams = tamanhos(modelo, tecido);
			if (tams.isEmpty())
			{
				continue;
			}
			final Map<String, Object> m = new LinkedHashMap<>();
			m.put("code", p.getCode());
			m.put("modelo", modelo);
			m.put("tecido", tecido);
			m.put("preco", price(p));
			m.put("tamanhos", tams);
			final List<Map<String, Object>> med = new ArrayList<>();
			for (final Map.Entry<String, int[]> x : medidas(modelo, tecido).entrySet())
			{
				final Map<String, Object> r = new LinkedHashMap<>();
				r.put("tamanho", x.getKey());
				r.put("largura", Integer.valueOf(x.getValue()[0]));
				r.put("comprimento", Integer.valueOf(x.getValue()[1]));
				med.add(r);
			}
			m.put("medidas", med);
			camisetas.add(m);
		}
		body.put("camisetas", camisetas);
		body.put("cores", CORES);
		return body;
	}

	@Override
	public Map<String, Object> estampaDto(final ItemModel e, final long now)
	{
		final Map<String, Object> m = new LinkedHashMap<>();
		final ItemModel drop = (ItemModel) attr(e, "drop");
		final ItemModel linha = (ItemModel) attr(e, "linha");
		final Object launch = attr(e, "launchDate");
		final long end = end(e);
		m.put("code", str(e, "code"));
		m.put("nome", str(e, "name"));
		m.put("drop", drop == null ? null : str(drop, "code"));
		m.put("linha", linha == null ? null : str(linha, "code"));
		m.put("numero", attr(e, "number"));
		m.put("lancamento", launch instanceof Date ? Long.valueOf(((Date) launch).getTime()) : null);
		m.put("encerramento", end == Long.MAX_VALUE ? null : Long.valueOf(end));
		m.put("situacao", situacao(e, now));
		m.put("posicao", StringUtils.defaultIfBlank(str(e, "position"), "FRENTE_INTEIRA"));
		final Object art = attr(e, "art");
		m.put("arte", art instanceof MediaModel ? ((MediaModel) art).getURL() : null);
		m.put("arteLarga", Boolean.valueOf(Boolean.TRUE.equals(attr(e, "wideArt"))));
		m.put("fundo", StringUtils.defaultIfBlank(str(e, "background"), "#111111"));
		m.put("coresIndisponiveis", blockedColors(e));
		m.put("descricao", str(e, "description"));
		return m;
	}

	@Override
	public ItemModel demoConfig()
	{
		final List<ItemModel> list = search("SELECT {pk} FROM {MengoDemoConfig} WHERE {code} = 'default'",
				Collections.emptyMap());
		return list.isEmpty() ? null : list.get(0);
	}

	protected Map<String, Long> forcedEnds()
	{
		final Map<String, Long> map = new LinkedHashMap<>();
		final ItemModel demo = demoConfig();
		final String text = demo == null ? null : str(demo, "forcedEnds");
		if (text != null)
		{
			for (final String line : text.split("\\r?\\n"))
			{
				final int eq = line.indexOf('=');
				if (eq > 0)
				{
					try
					{
						map.put(line.substring(0, eq).trim(), Long.valueOf(line.substring(eq + 1).trim()));
					}
					catch (final NumberFormatException e)
					{
						LOG.warn("Encerramento simulado inválido: {}", line);
					}
				}
			}
		}
		return map;
	}

	protected Double price(final ProductModel product)
	{
		try
		{
			final PriceInformation info = commercePriceService.getWebPriceForProduct(product);
			return info == null || info.getPriceValue() == null ? null : Double.valueOf(info.getPriceValue().getValue());
		}
		catch (final RuntimeException e)
		{
			LOG.warn("Sem preço para a camiseta {}", product.getCode());
			return null;
		}
	}

	protected CatalogVersionModel onlineCatalog()
	{
		try
		{
			return catalogVersionService.getCatalogVersion(
					configurationService.getConfiguration().getString("flamengo.estudio.catalogo", "flamengoProductCatalog"),
					"Online");
		}
		catch (final RuntimeException e)
		{
			LOG.warn("Catálogo do Mengo Estúdio não encontrado: {}", e.getMessage());
			return null;
		}
	}

	protected List<ItemModel> search(final String query, final Map<String, Object> params)
	{
		try
		{
			final FlexibleSearchQuery q = new FlexibleSearchQuery(query);
			q.addQueryParameters(params);
			return flexibleSearchService.<ItemModel> search(q).getResult();
		}
		catch (final RuntimeException e)
		{
			// tipo ainda não criado (antes do Update) ou consulta inválida: Estúdio vazio, loja segue
			LOG.warn("Mengo Estúdio: consulta falhou ({}): {}", query, e.getMessage());
			return Collections.emptyList();
		}
	}

	protected long cfgLong(final String key, final long def)
	{
		return configurationService.getConfiguration().getLong(key, def);
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
			return item == null ? null : modelService.getAttributeValue(item, qualifier);
		}
		catch (final RuntimeException e)
		{
			return null;
		}
	}

	public void setConfigurationService(final ConfigurationService configurationService)
	{
		this.configurationService = configurationService;
	}

	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}

	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}

	public void setCatalogVersionService(final CatalogVersionService catalogVersionService)
	{
		this.catalogVersionService = catalogVersionService;
	}

	public void setCommercePriceService(final CommercePriceService commercePriceService)
	{
		this.commercePriceService = commercePriceService;
	}
}
