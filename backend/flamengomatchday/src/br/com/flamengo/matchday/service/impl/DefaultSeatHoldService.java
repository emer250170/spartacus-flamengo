package br.com.flamengo.matchday.service.impl;

import br.com.flamengo.matchday.constants.FlamengomatchdayConstants;
import br.com.flamengo.matchday.model.SeatHoldModel;
import br.com.flamengo.matchday.service.SeatHoldService;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.ordersplitting.model.StockLevelModel;
import de.hybris.platform.ordersplitting.model.WarehouseModel;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.user.UserService;
import de.hybris.platform.stock.StockService;
import de.hybris.platform.stock.exception.InsufficientStockLevelException;
import de.hybris.platform.variants.model.VariantProductModel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class DefaultSeatHoldService implements SeatHoldService
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultSeatHoldService.class);

	private static final String FIND_BY_PRODUCT = "SELECT {" + SeatHoldModel.PK + "} FROM {" + SeatHoldModel._TYPECODE
			+ "} WHERE {" + SeatHoldModel.PRODUCT + "} = ?product";
	private static final String FIND_EXPIRED = "SELECT {" + SeatHoldModel.PK + "} FROM {" + SeatHoldModel._TYPECODE
			+ "} WHERE {" + SeatHoldModel.EXPIRESAT + "} < ?now";

	private ModelService modelService;
	private FlexibleSearchService flexibleSearchService;
	private StockService stockService;
	private UserService userService;
	private ConfigurationService configurationService;

	@Override
	public boolean isSeat(final ProductModel product)
	{
		if (!(product instanceof VariantProductModel) || product.getCode() == null)
		{
			return false;
		}
		final String prefix = configurationService.getConfiguration().getString(FlamengomatchdayConstants.SEAT_CODE_PREFIX,
				"JOGO-");
		return product.getCode().startsWith(prefix);
	}

	@Override
	public boolean hold(final ProductModel seat, final AbstractOrderModel cart, final boolean renew)
	{
		final SeatHoldModel existing = findHold(seat);
		if (existing != null)
		{
			if (isOwner(existing, cart))
			{
				existing.setCartCode(cart.getCode());
				existing.setUser(cart.getUser());
				if (renew)
				{
					existing.setExpiresAt(newExpiry());
				}
				modelService.save(existing);
				return true;
			}
			if (existing.getExpiresAt() != null && existing.getExpiresAt().after(new Date()))
			{
				return false;
			}
			// a reserva de outra pessoa venceu: solta antes de tentar reservar
			releaseHold(existing);
		}

		final StockLevelModel level = findStockLevel(seat);
		if (level == null)
		{
			return false;
		}
		final WarehouseModel warehouse = level.getWarehouse();
		try
		{
			// atomico no banco: so passa se ainda houver unidade livre
			stockService.reserve(seat, warehouse, 1, "matchday:reserva " + cart.getCode());
		}
		catch (final InsufficientStockLevelException e)
		{
			return false;
		}

		final SeatHoldModel hold = modelService.create(SeatHoldModel.class);
		hold.setProduct(seat);
		hold.setWarehouse(warehouse);
		hold.setCartCode(cart.getCode());
		hold.setUser(cart.getUser());
		hold.setExpiresAt(newExpiry());
		try
		{
			modelService.save(hold);
		}
		catch (final RuntimeException e)
		{
			LOG.warn("N\u00e3o foi poss\u00edvel gravar a reserva de {}: {}", seat.getCode(), e.getMessage());
			stockService.release(seat, warehouse, 1, "matchday:reserva desfeita " + cart.getCode());
			return false;
		}
		LOG.info("Poltrona {} reservada para o carrinho {}", seat.getCode(), cart.getCode());
		return true;
	}

	@Override
	public void releaseIfHeldElsewhereBySameUser(final ProductModel seat, final AbstractOrderModel cart)
	{
		final SeatHoldModel existing = findHold(seat);
		if (existing != null && !cart.getCode().equals(existing.getCartCode()) && isSameRegisteredUser(existing.getUser(),
				cart.getUser()))
		{
			releaseHold(existing);
		}
	}

	@Override
	public void release(final ProductModel seat, final AbstractOrderModel cart)
	{
		final SeatHoldModel existing = findHold(seat);
		if (existing != null && cart.getCode().equals(existing.getCartCode()))
		{
			releaseHold(existing);
		}
	}

	@Override
	public void commitSale(final AbstractOrderModel order)
	{
		if (FlamengomatchdayConstants.STOCK_MARKER.equals(order.getStatusInfo()))
		{
			return;
		}
		for (final AbstractOrderEntryModel entry : order.getEntries())
		{
			final ProductModel product = entry.getProduct();
			final int quantity = entry.getQuantity() == null ? 0 : entry.getQuantity().intValue();
			if (product == null || quantity <= 0)
			{
				continue;
			}
			final SeatHoldModel hold = isSeat(product) ? findHold(product) : null;
			final StockLevelModel level = hold != null ? stockService.getStockLevel(product, hold.getWarehouse())
					: findStockLevel(product);
			if (level == null)
			{
				continue;
			}
			if (hold != null)
			{
				// a unidade sai de "reservada" e vira venda
				modelService.remove(hold);
				stockService.release(product, level.getWarehouse(), quantity, "matchday:venda " + order.getCode());
			}
			modelService.refresh(level);
			level.setAvailable(Math.max(0, level.getAvailable() - quantity));
			modelService.save(level);
		}
		order.setStatusInfo(FlamengomatchdayConstants.STOCK_MARKER);
		modelService.save(order);
		LOG.info("Estoque baixado para o pedido {}", order.getCode());
	}

	@Override
	public int releaseExpired()
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery(FIND_EXPIRED);
		query.addQueryParameter("now", new Date());
		final List<SeatHoldModel> expired = new ArrayList<>(flexibleSearchService.<SeatHoldModel> search(query).getResult());
		int released = 0;
		for (final SeatHoldModel hold : expired)
		{
			if (releaseHold(hold))
			{
				released++;
			}
		}
		return released;
	}

	protected boolean releaseHold(final SeatHoldModel hold)
	{
		final ProductModel product = hold.getProduct();
		final WarehouseModel warehouse = hold.getWarehouse();
		final String cartCode = hold.getCartCode();
		try
		{
			// apaga primeiro: se duas rotinas tentarem soltar a mesma reserva, so uma devolve a unidade
			modelService.remove(hold);
		}
		catch (final RuntimeException e)
		{
			LOG.debug("Reserva j\u00e1 liberada: {}", e.getMessage());
			return false;
		}
		if (product != null && warehouse != null)
		{
			final StockLevelModel level = stockService.getStockLevel(product, warehouse);
			if (level != null)
			{
				modelService.refresh(level);
				if (level.getReserved() > 0)
				{
					stockService.release(product, warehouse, 1, "matchday:liberada " + cartCode);
				}
			}
			LOG.info("Poltrona {} liberada (carrinho {})", product.getCode(), cartCode);
		}
		return true;
	}

	protected SeatHoldModel findHold(final ProductModel seat)
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery(FIND_BY_PRODUCT);
		query.addQueryParameter("product", seat);
		final List<SeatHoldModel> result = flexibleSearchService.<SeatHoldModel> search(query).getResult();
		return result.isEmpty() ? null : result.get(0);
	}

	protected StockLevelModel findStockLevel(final ProductModel product)
	{
		final Collection<StockLevelModel> levels = stockService.getAllStockLevels(product);
		if (levels == null || levels.isEmpty())
		{
			return null;
		}
		final String preferred = configurationService.getConfiguration().getString(FlamengomatchdayConstants.WAREHOUSE,
				"warehouse_s");
		for (final StockLevelModel level : levels)
		{
			if (level.getWarehouse() != null && preferred.equals(level.getWarehouse().getCode()))
			{
				return level;
			}
		}
		return levels.iterator().next();
	}

	protected boolean isOwner(final SeatHoldModel hold, final AbstractOrderModel cart)
	{
		return cart.getCode().equals(hold.getCartCode()) || isSameRegisteredUser(hold.getUser(), cart.getUser());
	}

	protected boolean isSameRegisteredUser(final UserModel a, final UserModel b)
	{
		return a != null && b != null && a.equals(b) && !userService.isAnonymousUser(a);
	}

	protected Date newExpiry()
	{
		final int minutes = configurationService.getConfiguration().getInt(FlamengomatchdayConstants.HOLD_MINUTES, 15);
		return new Date(System.currentTimeMillis() + minutes * 60_000L);
	}

	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}

	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}

	public void setStockService(final StockService stockService)
	{
		this.stockService = stockService;
	}

	public void setUserService(final UserService userService)
	{
		this.userService = userService;
	}

	public void setConfigurationService(final ConfigurationService configurationService)
	{
		this.configurationService = configurationService;
	}
}
