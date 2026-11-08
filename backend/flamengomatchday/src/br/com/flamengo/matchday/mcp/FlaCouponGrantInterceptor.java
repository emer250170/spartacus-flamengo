package br.com.flamengo.matchday.mcp;

import de.hybris.platform.core.model.ItemModel;
import de.hybris.platform.servicelayer.interceptor.InterceptorContext;
import de.hybris.platform.servicelayer.interceptor.InterceptorException;
import de.hybris.platform.servicelayer.interceptor.PrepareInterceptor;


/**
 * Cupom pessoal pedido por um gestor pelo Claude (MCP), tipo FlaCouponGrant: ao ser criado, confere tudo
 * ({@link FlaCouponGrantService#validate}; recusa = nada é gravado) e, depois da gravação, cria o cupom e a
 * promoção, publica e envia o e-mail ao cliente ({@link FlaCouponGrantService#publishAndSend}). Alterações
 * depois de criado não refazem nada.
 */
public class FlaCouponGrantInterceptor implements PrepareInterceptor<ItemModel>
{
	private FlaCouponGrantService flaCouponGrantService;
	private McpSupport mcpSupport;

	@Override
	public void onPrepare(final ItemModel grant, final InterceptorContext ctx) throws InterceptorException
	{
		if (!ctx.isNew(grant))
		{
			return;
		}
		try
		{
			flaCouponGrantService.validate(grant);
		}
		catch (final IllegalArgumentException e)
		{
			throw new InterceptorException(e.getMessage());
		}
		final Object code = ctx.getModelService().getAttributeValue(grant, "code");
		mcpSupport.afterCommit(FlaCouponGrantService.TYPE, String.valueOf(code), flaCouponGrantService::publishAndSend);
	}

	public void setFlaCouponGrantService(final FlaCouponGrantService flaCouponGrantService)
	{
		this.flaCouponGrantService = flaCouponGrantService;
	}

	public void setMcpSupport(final McpSupport mcpSupport)
	{
		this.mcpSupport = mcpSupport;
	}
}
