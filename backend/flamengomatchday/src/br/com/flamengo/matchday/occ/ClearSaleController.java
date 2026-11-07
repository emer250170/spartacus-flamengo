package br.com.flamengo.matchday.occ;

import br.com.flamengo.matchday.clearsale.ClearSaleService;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;


/**
 * ClearSale na loja (07/11): GET /{baseSiteId}/flamengo/clearsale devolve {"ativo", "fingerprint"}. Com a
 * análise ligada (Backoffice, aba ClearSale da loja base), o checkout carrega o script de identificação do
 * aparelho com essa chave pública e usa o guid do carrinho como sessão.
 */
@Controller
@RequestMapping(value = "/{baseSiteId}/flamengo/clearsale")
public class ClearSaleController
{
	private ClearSaleService clearSaleService;

	@RequestMapping(method = RequestMethod.GET)
	@ResponseBody
	public ResponseEntity<Map<String, Object>> config(@PathVariable final String baseSiteId)
	{
		return ResponseEntity.ok(clearSaleService.configLoja());
	}

	public void setClearSaleService(final ClearSaleService clearSaleService)
	{
		this.clearSaleService = clearSaleService;
	}
}
