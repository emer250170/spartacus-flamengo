package br.com.flamengo.matchday.util;

import java.lang.reflect.Method;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Guarda e devolve o "contexto de operação" do Backoffice na thread atual.
 * <p>
 * O ImpEx síncrono roda como CronJob e abre e fecha uma sessão na própria thread. No Backoffice, fechar uma sessão
 * apaga esse contexto (FallbackRequestOperationScopeController.sessionClosed); depois de salvar, a lista que se
 * atualiza falha com "There is no context assigned to current thread" e aparece "Erro inesperado". Fora do Backoffice
 * (HAC, OCC, cronjobs) a classe não existe e nada é feito.
 * <p>
 * Uso: {@code final Runnable restore = BackofficeRequestContext.capture(); try { ... } finally { restore.run(); }}
 * <p>
 * Reflexão porque o core não depende das bibliotecas do Backoffice.
 */
public final class BackofficeRequestContext
{
	private static final Logger LOG = LoggerFactory.getLogger(BackofficeRequestContext.class);
	private static final String HOLDER = "com.hybris.cockpitng.core.spring.RequestOperationContextHolder";
	private static final Runnable NOTHING = () -> {
		// sem contexto do Backoffice nesta thread
	};

	private BackofficeRequestContext()
	{
	}

	public static Runnable capture()
	{
		try
		{
			final ClassLoader loader = Thread.currentThread().getContextClassLoader();
			final Class<?> holderClass = Class.forName(HOLDER, true,
					loader != null ? loader : BackofficeRequestContext.class.getClassLoader());
			final Object holder = holderClass.getMethod("instance").invoke(null);
			final Method getContext = holderClass.getMethod("getContext");
			final Object context = getContext.invoke(holder);
			if (context == null)
			{
				return NOTHING;
			}
			final Object request = holderClass.getMethod("prepareForOperation").invoke(holder);
			final Method attach = holderClass.getMethod("attachToContext", request.getClass());
			return () -> {
				try
				{
					if (!context.equals(getContext.invoke(holder)))
					{
						attach.invoke(holder, request);
					}
				}
				catch (final Exception e)
				{
					LOG.warn("Não foi possível devolver o contexto do Backoffice à thread", e);
				}
			};
		}
		catch (final ClassNotFoundException | LinkageError e)
		{
			return NOTHING;
		}
		catch (final Exception e)
		{
			LOG.debug("Contexto do Backoffice indisponível", e);
			return NOTHING;
		}
	}
}
