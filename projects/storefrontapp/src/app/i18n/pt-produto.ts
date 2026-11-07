import { TranslationResources } from '@spartacus/core';

/**
 * Português da página de produto (protótipo Flamengo).
 *
 * O Spartacus só traz traduções em inglês; o site em português caía no
 * inglês (fallbackLang). Aqui ficam só as chaves que aparecem na página de
 * produto e na busca — o que faltar continua vindo do inglês.
 * Chunks: product, pickupInStore, productImageZoom, common.
 */
export const ptProduto: TranslationResources = {
  pt: {
    product: {
      productDetails: {
        id: 'Código',
        quantity: 'Qtd',
        quantityFull: 'Quantidade',
        productDetails: 'Detalhes do produto',
        specification: 'Especificações',
        reviews: 'Avaliações',
        shipping: 'Entrega',
        share: 'Compartilhar',
        showReviews: 'Ver avaliações',
        noReviews: 'Ainda sem avaliações',
        productPrice: 'Preço do produto',
        noProductImage: 'Imagem indisponível, {{ product }}',
      },
      productSummary: {
        id: 'Código',
        showReviews: 'Ver avaliações',
        showReviewsDetailed:
          'Ver {{count}} avaliações, nota {{rating}} de 5 estrelas',
        share: 'Compartilhar',
        newItemPrice: 'Preço do item novo',
      },
      productReview: {
        overallRating: 'Nota geral',
        reviewTitle: 'Título da avaliação',
        writeYourComments: 'Escreva seu comentário',
        rating: 'Nota',
        ratingRequired: 'Nota do produto, obrigatória',
        addRate: 'Dar nota: {{count}} estrela',
        addRate_other: 'Dar nota: {{count}} estrelas',
        reviewerName: 'Seu nome (opcional)',
        writeReview: 'Escrever avaliação',
        more: 'Ver mais avaliações',
        less: 'Ver menos avaliações',
        thankYouForReview:
          'Obrigado pela avaliação! Ela pode passar por moderação antes de aparecer aqui.',
        postReviewFail:
          'Não foi possível enviar sua avaliação. Tente de novo mais tarde.',
        ratedOutOf: 'Nota: {{rating}} de 5',
      },
      addToCart: {
        itemAddedToYourCart: 'Item adicionado ao carrinho',
        itemsAddedToYourCart: 'Item(ns) adicionado(s) ao carrinho',
        itemsIncrementedInYourCart:
          'Este item já estava no carrinho. A quantidade foi atualizada.',
        items: 'itens',
        updatingCart: 'Atualizando o carrinho...',
        addToCart: 'Adicionar ao carrinho',
        viewCart: 'ver carrinho',
        proceedToCheckout: 'finalizar compra',
        quantity: 'Qtd',
        quantityFull: 'Quantidade',
        outOfStock: 'Esgotado',
        inStock: 'Em estoque',
        selectStyleAndSize: 'Escolha modelo e tamanho para ver o estoque',
        removeFromCart: 'Remover do carrinho',
        closeModal: 'Fechar',
        buyItAgain: 'Comprar de novo',
        addToActiveCart: 'Adicionar ao carrinho atual',
      },
      TabPanelContainer: {
        tabs: {
          ProductDetailsTabComponent: 'Sobre o produto',
          ProductSpecsTabComponent: 'Especificações',
          ProductReviewsTabComponent: 'Avaliações',
          deliveryTab: 'Entrega',
          SparePartsTabComponent: 'Peças de reposição',
        },
        tabPanelContainerRegion: 'Abas com mais detalhes do produto',
        tabPanelContainerRegionGroup: 'Grupo com mais detalhes do produto',
      },
      addToWishList: {
        add: 'Adicionar à lista de desejos',
        remove: 'Remover da lista de desejos',
        anonymous: 'Entre na sua conta para salvar na lista de desejos',
        addedToWishList: 'Produto adicionado à lista de desejos',
        removedFromWishList: 'Produto removido da lista de desejos',
      },
      itemCounter: {
        removeOne: 'Menos um',
        addOneMore: 'Mais um',
        quantity: 'Quantidade',
      },
      // página de busca e de categoria
      productList: {
        filterBy: {
          label: 'Filtrar por',
          action: 'Filtrar por',
        },
        activeFilter: 'Filtro {{filter}}; clique para remover o filtro',
        appliedFilter: 'Filtro aplicado:',
        showLess: 'Ver menos...',
        showMore: 'Ver mais...',
        sortBy: 'Ordenar por',
        sortResults: 'Ordenar resultados',
        backToTopBtn: 'VOLTAR AO TOPO',
        showMoreBtn: 'Carregar mais produtos',
        productSearchPagination: 'Paginação da busca de produtos',
        productListResults: 'Lista de produtos encontrados',
      },
      productFacetNavigation: {
        filterBy: {
          label: 'Filtrar por',
          action: 'Filtrar por',
          facet: 'Filtrar resultados',
          name: 'Filtrar por {{name}}',
        },
        appliedFilter: 'Filtro aplicado:',
        showLess: 'Ver menos...',
        showMore: 'Ver mais...',
        sortBy: 'Ordenar por',
        ariaLabelShowLess:
          'Ver menos; mostra menos opções do grupo selecionado',
        ariaLabelShowMore:
          'Ver mais; mostra todas as opções do grupo selecionado',
        ariaLabelItemsAvailable:
          '{{name}}, {{state}} {{count}} item disponível',
        ariaLabelItemsAvailable_other:
          '{{name}}, {{state}} {{count}} itens disponíveis',
        decreaseOptionsVisibility:
          'Algumas opções do grupo foram ocultadas; volte com Tab para lê-las ou avance para o próximo grupo',
        increaseOptionsVisibility:
          'Mais opções foram adicionadas ao grupo; volte com Tab para lê-las ou avance para o próximo grupo',
        backToResults: 'Voltar aos resultados',
        productFacets: 'Filtros de produtos',
      },
      productView: {
        gridView: 'Mostrar em grade',
        listView: 'Mostrar em lista',
      },
    },
    pickupInStore: {
      pickupOptions: {
        legend: 'Forma de entrega',
        changeStore: 'Trocar loja',
        delivery: 'Receber em casa (troca grátis)',
        pickup: 'Retirar grátis na loja',
        shipIt: 'Receber em casa',
        selectStore: 'Escolher loja',
        freeReturn: 'Troca grátis',
        storeIsNotSelected: 'Nenhuma loja escolhida para retirada.',
      },
      // janela "Retirar na loja" (escolha da loja de retirada)
      pickupOptionDialog: {
        close: 'Fechar',
        modalHeader: 'Retirar na loja',
      },
      storeSearch: {
        findAStore: 'Encontre uma loja',
        findStores: 'Buscar lojas',
        hideOutOfStockOptions: 'Ocultar lojas sem estoque',
        searchPlaceholder: 'Digite o CEP, a cidade ou o endereço',
        useMyLocation: 'Usar minha localização',
      },
      storeList: {
        noStoresMessage: 'Nenhuma loja encontrada.',
      },
      store: {
        pickupFromHere: 'Retirar aqui',
        stockLevel_inStock: '{{ count }} em estoque',
        stockLevel_outOfStock: 'Sem estoque',
        viewHours: 'Ver horários',
      },
      storeSchedule: {
        closed: 'Fechado',
        storeHours: 'Horário de funcionamento',
      },
      setPreferredStore: {
        makeThisMyStore: 'Definir como minha loja',
        myStore: 'Minha loja',
      },
      storeFinderPickupInStore: {
        heading: 'Minha loja',
      },
      addressBookPickupInStore: {
        heading: 'Loja de retirada',
      },
      cardActions: {
        getDirections: 'Como chegar (abre em uma nova janela)',
      },
    },
    productImageZoom: {
      productImageZoomTrigger: { expand: 'Ampliar imagem' },
      productImageZoomDialog: {
        zoomedInImage: 'Imagem ampliada',
        previousSlide: 'Imagem anterior',
        nextSlide: 'Próxima imagem',
        close: 'Fechar',
      },
    },
    common: {
      common: {
        search: 'Buscar',
        expand: 'Expandir',
        collapse: 'Recolher',
        selected: 'Selecionado',
      },
      searchBox: {
        placeholder: 'Busque por produto ou código',
        productSearch: 'Encontre um produto',
        ariaLabelInput: 'Buscar aqui...',
        suggestions: 'Sugestões',
        products: 'Produtos recomendados',
        resetLabel: 'Limpar busca',
        help: {
          insufficientChars: 'Digite mais algumas letras',
          noMatch: 'Não encontramos resultados',
          exactMatch: '{{ term }}',
          empty: 'Pergunte o que quiser',
        },
        closeSearchPanel: 'Fechar',
      },
    },
  },
};
