package br.com.flamengo.matchday.occ;

import br.com.flamengo.matchday.emarsys.EmarsysCatalogService;
import de.hybris.platform.servicelayer.config.ConfigurationService;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;


/**
 * Catálogo de produtos para o Emarsys, público (sem login), num endereço fixo para o Emarsys baixar:
 * <ul>
 * <li>GET /{baseSiteId}/emarsys/produtos.csv (ou /produtos): o CSV no formato do Emarsys;</li>
 * <li>GET /{baseSiteId}/emarsys/categorias: caminho de cada categoria ({"codigo": "A > B"}), que a loja
 * usa no comando category do Web Extend para casar com o campo category do CSV.</li>
 * </ul>
 * O conteúdo fica em cache por flamengo.emarsys.catalogo.cache.minutos (60). Chave
 * flamengo.emarsys.catalogo.ativo (true): desligada, as duas respostas são 404.
 */
@Controller
@RequestMapping(value = "/{baseSiteId}/emarsys")
public class EmarsysCatalogController
{
	private EmarsysCatalogService emarsysCatalogService;
	private ConfigurationService configurationService;

	@RequestMapping(value = { "/produtos.csv", "/produtos" }, method = RequestMethod.GET)
	@ResponseBody
	public ResponseEntity<byte[]> products(@PathVariable final String baseSiteId)
	{
		if (!enabled())
		{
			return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
		}
		final byte[] body = emarsysCatalogService.productCatalogCsv().getBytes(StandardCharsets.UTF_8);
		return ResponseEntity.ok()
				.contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
				.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"emarsys-produtos.csv\"")
				.cacheControl(CacheControl.maxAge(30, TimeUnit.MINUTES).cachePublic())
				.body(body);
	}

	@RequestMapping(value = "/categorias", method = RequestMethod.GET)
	@ResponseBody
	public ResponseEntity<Map<String, String>> categories(@PathVariable final String baseSiteId)
	{
		if (!enabled())
		{
			return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
		}
		return ResponseEntity.ok()
				.cacheControl(CacheControl.maxAge(30, TimeUnit.MINUTES).cachePublic())
				.body(emarsysCatalogService.categoryPaths());
	}

	private boolean enabled()
	{
		return configurationService.getConfiguration().getBoolean("flamengo.emarsys.catalogo.ativo", true);
	}

	public void setEmarsysCatalogService(final EmarsysCatalogService emarsysCatalogService)
	{
		this.emarsysCatalogService = emarsysCatalogService;
	}

	public void setConfigurationService(final ConfigurationService configurationService)
	{
		this.configurationService = configurationService;
	}
}
