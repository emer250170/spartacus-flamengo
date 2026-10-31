import { TranslationResources } from '@spartacus/core';

/**
 * Português das páginas de "Minha conta" (protótipo Flamengo).
 *
 * Pedidos e detalhe do pedido, devoluções, lista de desejos, carrinhos salvos,
 * catálogo de endereços (loja de retirada), pagamento, dados pessoais, senha,
 * e-mail, consentimentos, encerrar conta, interesses, preferências de
 * notificação, cupons e solicitações de atendimento, além de textos comuns
 * (navegação, busca, erros) usados nessas páginas. Os títulos das páginas vêm
 * do conteúdo do servidor.
 * Chunks: common, myAccount, payment, user, address, storeFinder, order,
 * wishlist, savedCart, importExport, pickupInStore, customerTicketing.
 */
export const ptMinhaConta: TranslationResources = {
  pt: {
    common: {
      common: {
        breadcrumbs: 'trilha de navegação',
        selectFile: 'Escolher arquivo',
        loaded: 'Carregado',
        zoomIn: 'Ampliar',
        zoomOut: 'Reduzir',
      },
      pageMetaResolver: {
        category: {
          title: '{{count}} resultado para {{query}}',
          title_other: '{{count}} resultados para {{query}}',
        },
        checkout: {
          title: 'Finalizar compra',
        },
        search: {
          title: '{{count}} resultado para "{{query}}"',
          title_other: '{{count}} resultados para "{{query}}"',
          findProductTitle: '{{count}} resultado para o cupom "{{coupon}}"',
          findProductTitle_other:
            '{{count}} resultados para o cupom "{{coupon}}"',
          default_title: 'Todos os produtos',
        },
        product: {
          description: '{{description}}',
          heading: '{{heading}}',
          title: '{{title}}',
        },
      },
      spinner: {
        loading: 'Carregando...',
      },
      navigation: {
        categoryNavLabel: 'Menu de categorias',
        footerNavLabel: 'Links do rodapé',
        goTo: 'Ir para {{location}}',
        navigateTo: 'Ir para {{nav}}',
        scrollToTop: 'Voltar ao topo da página',
        linkItemInList: '{{title}}. {{position}} de {{listLength}}',
      },
      searchBox: {
        ariaLabelSuggestions: 'sugestões de busca',
        ariaLabelProducts: 'produtos encontrados',
        initialDescription:
          'Quando houver sugestões, use as setas para cima e para baixo para navegar e Enter para selecionar. Em telas sensíveis ao toque, explore tocando ou deslizando o dedo.',
        suggestionsResult: '{{ count }} sugestão e ',
        suggestionsResult_other: '{{ count }} sugestões e ',
        productsResult: '{{ count }} produto disponível.',
        productsResult_other: '{{ count }} produtos disponíveis.',
      },
      sorting: {
        date: 'Data',
        orderNumber: 'Número do pedido',
        rma: 'Número da devolução',
        replenishmentNumber: 'Número da reposição',
        nextOrderDate: 'Data do próximo pedido',
        pageViewUpdated: 'A página foi atualizada com as opções escolhidas.',
      },
      httpHandlers: {
        badGateway: 'Ocorreu um erro no servidor. Tente de novo mais tarde.',
        badRequestOldPasswordIncorrect: 'Senha atual incorreta.',
        badRequestGuestDuplicateEmail:
          'O e-mail {{ errorMessage }} já está cadastrado. Use outro e-mail para comprar como visitante.',
        conflict: 'Já existe.',
        forbidden:
          'Você não tem permissão para fazer isso. Se achar que é um engano, fale com o administrador.',
        gatewayTimeout: 'O servidor não respondeu. Tente de novo mais tarde.',
        internalServerError:
          'Ocorreu um erro interno no servidor. Tente de novo mais tarde.',
        unknownError: 'Ocorreu um erro desconhecido',
        validationErrors: {
          missing: {
            card_cardType:
              'Este cartão de crédito não é aceito. Escolha outro.',
            card_accountNumber: 'O número do cartão de crédito não é válido.',
            card_cvNumber: 'O código de segurança não é válido.',
            card_expirationMonth:
              'A validade do cartão de crédito não é válida.',
            card_expirationYear:
              'A validade do cartão de crédito não é válida.',
            billTo_firstName: 'O nome informado não é válido.',
            billTo_lastName: 'O sobrenome informado não é válido.',
            billTo_street1: 'O endereço informado não é válido.',
            billTo_street2: 'O endereço informado não é válido.',
            billTo_city:
              'A cidade informada não é válida para este cartão de crédito.',
            billTo_state:
              'O estado informado não é válido para este cartão de crédito.',
            billTo_country:
              'O país/região informado não é válido para este cartão de crédito.',
            billTo_postalCode:
              'O CEP não é válido para este cartão de crédito.',
            country: {
              isocode: 'Informe o país/região',
            },
          },
          invalid: {
            card_expirationMonth:
              'A validade do cartão de crédito não é válida.',
            firstName: 'O nome informado não é válido.',
            lastName: 'O sobrenome informado não é válido.',
            password: 'A senha informada não é válida.',
            uid: 'Identificador de usuário inválido.',
            code: 'Código inválido.',
            email: 'E-mail inválido.',
            loginId: 'E-mail inválido.',
          },
        },
        cartNotFound: 'Carrinho não encontrado.',
        invalidCodeProvided: 'Código inválido.',
        voucherExceeded: 'Este cupom já atingiu o limite de usos',
        unknownIdentifier: 'Item não encontrado.',
        otherCartErrors: 'Ocorreram erros no carrinho.',
      },
      miniCart: {
        total: '{{total}}',
        count: '{{count}}',
      },
      skipLink: {
        skipTo: 'Pular para',
        labels: {
          header: 'Cabeçalho',
          main: 'Conteúdo principal',
          footer: 'Rodapé',
          productFacets: 'Filtros de produtos',
          productList: 'Lista de produtos',
        },
      },
      carousel: {
        previousSlide: 'Slide anterior',
        nextSlide: 'Próximo slide',
        slideNumber:
          'O slide {{currentSlideNumber}} tem {{numberOfSlides}} itens',
        carouselForProduct: 'Carrossel de imagens de {{product}}',
      },
      formErrors: {
        labeled: {
          date: {
            required: 'O campo {{label}} é obrigatório',
            min: 'O campo {{label}} não pode ser anterior a {{min}}',
            max: 'O campo {{label}} não pode ser posterior a {{max}}',
            pattern: 'Use o formato de data yyyy-mm-dd no campo {{label}}',
            invalid: 'Use uma data válida no campo {{label}}',
          },
        },
        cxStarRatingEmpty: 'Dê uma nota',
        cxNoSelectedItemToCancel: 'Selecione pelo menos um item',
        cxNegativeAmount: 'O valor deve ser igual ou maior que zero',
        cxContainsSpecialCharacters:
          'O campo não pode ter caracteres especiais',
        date: {
          required: 'Campo obrigatório',
          min: 'A data não pode ser anterior a {{min}}',
          max: 'A data não pode ser posterior a {{max}}',
          pattern: 'Use o formato de data yyyy-mm-dd',
          invalid: 'Use uma data válida',
        },
        file: {
          required: 'Escolha um arquivo',
          empty: 'O arquivo não pode estar vazio',
          invalidExtension: 'Extensão de arquivo inválida',
          tooLarge: 'O arquivo pode ter no máximo {{ maxSize }} MB',
          tooManyEntries: 'O número de itens passa de {{ maxEntries }}',
          notParsable: 'Não foi possível ler o arquivo',
          fileNotAllowed: 'Este tipo de arquivo não é permitido',
        },
      },
      errorHandlers: {
        scriptFailedToLoad: 'Não foi possível carregar o script.',
        refreshThePage: 'Atualize a página.',
      },
      assistiveMessage: {
        actionCancelled: 'Ação cancelada. Nada foi alterado.',
      },
      generalErrors: {
        pageFailure:
          'Não foi possível carregar a página. Tente de novo mais tarde.',
      },
      chatMessaging: {
        charactersLeft: 'caracteres restantes: {{count}}',
        addNewMessage: 'Nova mensagem',
        send: 'Enviar',
        uploadFile: 'Anexar arquivo',
        informationLabel: '{{author}}. {{text}} em {{date}}',
        messages: 'Mensagens',
        addMessagePlaceHolder: 'Digite sua mensagem...',
        characterLimitAlert: 'Limite de caracteres atingido.',
        a11y: {
          itemListBoxLabel: 'Selecione o item ao qual a mensagem se refere.',
        },
      },
    },
    myAccount: {
      closeAccount: {
        confirmAccountClosure: 'Confirmar encerramento da conta',
        confirmAccountClosureMessage: 'Quer mesmo encerrar sua conta?',
        closeMyAccount: 'Encerrar minha conta',
        accountClosedSuccessfully: 'Conta encerrada',
        accountClosedFailure: 'Não foi possível encerrar a conta',
      },
      updatePasswordForm: {
        oldPassword: {
          label: 'Senha atual',
          placeholder: 'Senha atual',
        },
        oldPasswordIsRequired: 'Informe a senha atual.',
        newPassword: {
          label: 'Nova senha',
          placeholder: 'Nova senha',
        },
        passwordMinRequirements:
          'Dica para uma senha segura: use pelo menos 8 caracteres, misturando letras maiúsculas e minúsculas, números e símbolos.',
        passwordStrengthRequirements:
          'Dica para uma senha segura: use pelo menos 8 caracteres, misturando letras maiúsculas e minúsculas, números e símbolos, sem repetir o mesmo caractere em sequência.',
        confirmPassword: {
          label: 'Confirmar nova senha',
          placeholder: 'Repita a nova senha',
        },
        bothPasswordMustMatch: 'As duas senhas precisam ser iguais',
        passwordUpdateSuccess: 'Senha atualizada',
        accessDeniedError: 'Acesso negado',
      },
      updateProfileForm: {
        title: 'Tratamento',
        none: '',
        firstName: {
          label: 'Nome',
          placeholder: 'Nome',
        },
        firstNameIsRequired: 'Informe o nome.',
        lastName: {
          label: 'Sobrenome',
          placeholder: 'Sobrenome',
        },
        lastNameIsRequired: 'Informe o sobrenome.',
        profileUpdateSuccess: 'Dados pessoais atualizados',
        customerId: 'Nº do cliente',
      },
      consentManagementForm: {
        clearAll: 'Desmarcar todos',
        selectAll: 'Marcar todos',
        message: {
          success: {
            given: 'Consentimento concedido.',
            withdrawn: 'Consentimento retirado.',
          },
        },
      },
      myCoupons: {
        noCouponsMessage: 'Você não tem cupons disponíveis.',
        effectiveTitle: 'Validade:',
        Effective: 'EM VIGOR',
        PreSession: 'EM BREVE',
        ExpireSoon: 'EXPIRA EM BREVE',
        readMore: 'Saiba mais',
        notification: 'Notificação',
        findProducts: 'Ver produtos',
        status: 'Situação:',
        dialogTitle: 'Cupom',
        claimCustomerCoupon: 'Cupom resgatado.',
        myCoupons: 'Meus cupons',
        startDateAsc: 'Data de início (crescente)',
        startDateDesc: 'Data de início (decrescente)',
        endDateAsc: 'Data de término (crescente)',
        endDateDesc: 'Data de término (decrescente)',
        sortBy: 'Ordenar por',
        sortCoupons: 'Ordenar cupons',
        notesPreffix:
          'Você pode escolher por onde prefere receber avisos de cupons na página ',
        notesLink: 'Preferências de notificação',
        notesSuffix: '.',
      },
      notificationPreference: {
        message: 'Escolha por onde prefere receber notificações',
        note: 'Observação: ',
        noteMessage:
          'Se você desativar todos os canais, não vai receber mais nenhuma notificação.',
        EMAIL: 'E-mail:',
        SMS: 'SMS:',
        SITE_MESSAGE: 'Mensagem no site',
      },
      myInterests: {
        header: 'Meus interesses',
        item: 'ITEM',
        price: 'PREÇO',
        notifications: 'NOTIFICAÇÕES',
        noInterests: 'Você ainda não registrou nenhum interesse.',
        inStock: 'Em estoque',
        lowStock: 'Estoque baixo',
        outOfStock: 'Esgotado',
        BACK_IN_STOCK: 'De volta ao estoque',
        sortBy: 'Ordenar por',
        sortInterests: 'Ordenar interesses',
        expirationDate: ' - Até {{ expirationDate }}',
        productId: 'Código {{ code }}',
        remove: 'Remover',
        sorting: {
          byNameAsc: 'Nome (crescente)',
          byNameDesc: 'Nome (decrescente)',
        },
        actions: 'AÇÕES',
        caption: 'Itens de Meus interesses.',
        itemRemoved: 'O item selecionado foi removido.',
      },
    },
    payment: {
      paymentMethods: {
        invalidField: 'Campo inválido: {{ field }}',
      },
    },
    user: {
      authMessages: {
        signedOutSuccessfully: 'Você saiu da sua conta.',
      },
    },
    address: {
      addressMessages: {
        setAsDefaultSuccessfully:
          'O endereço {{ streetAddress }} agora é o padrão',
      },
    },
    storeFinder: {
      storeFinder: {
        openUntil: 'Aberto até',
        closed: 'Fechado',
        back: 'Voltar',
        call: 'Ligar',
        getDirections: 'Como chegar',
        ariaLabelGetDirections: 'Como chegar (abre em uma nova janela)',
        ariaLabelCountriesCount: 'Lojas encontradas em {{ count }} países',
        listView: 'Ver em lista',
        mapView: 'Ver no mapa',
        noStoresFound: 'Nenhuma loja encontrada.',
        storeHours: 'Horário de funcionamento',
        storeFeatures: 'Serviços da loja',
        storeFinder: 'Encontre uma loja',
        storesFoundTitle: 'Lojas encontradas',
        storesFound: '{{ count }} loja encontrada',
        storesFound_other: '{{ count }} lojas encontradas',
        fromStoresFound: 'de {{ count }} loja encontrada',
        fromStoresFound_other: 'de {{ count }} lojas encontradas',
        findStore: 'Buscar loja',
        findStoreBy: 'Buscar loja por',
        useMyLocation: 'Usar minha localização',
        viewAllStores: 'Ver todas as lojas',
        contactUs: 'Fale conosco',
        searchBox: 'CEP, cidade ou endereço',
        backToList: 'Voltar à lista',
        noStoresMessage: 'Nenhuma loja cadastrada...',
        geolocationNotEnabled:
          'Não foi possível encontrar lojas próximas. Você não autorizou o uso da sua localização',
        searchNearestStores: 'Buscar lojas próximas',
        searchBoxLabel: 'Digite o CEP, a cidade ou o endereço',
      },
    },
    order: {
      orderDetails: {
        replenishmentId: 'Reposição nº',
        purchaseOrderId: 'Ordem de compra nº',
        purchaseOrderNumber: 'Número da ordem de compra',
        emptyPurchaseOrderId: 'Nenhuma',
        none: 'Nenhum {{value}}',
        unit: 'Unidade',
        costCenter: 'Centro de custo',
        costCenterAndUnit: 'Centro de custo / Unidade',
        costCenterAndUnitValue: '{{costCenterName}} / {{unitName}}',
        payByAccount: 'Faturado',
        active: 'Ativo',
        startOn: 'Início em',
        nextOrderDate: 'Data do próximo pedido',
        frequency: 'Frequência',
        deliveryStatus_IN_TRANSIT: 'Em trânsito',
        deliveryStatus_READY_FOR_PICKUP: 'Pronto para retirada',
        deliveryStatus_READY_FOR_SHIPPING: 'Pronto para envio',
        deliveryStatus_WAITING: 'Aguardando',
        deliveryStatus_DELIVERING: 'Em entrega',
        deliveryStatus_PICKPACK: 'Preparando para envio',
        deliveryStatus_PICKUP_COMPLETE: 'Retirada concluída',
        deliveryStatus_DELIVERY_COMPLETED: 'Entrega concluída',
        deliveryStatus_PAYMENT_NOT_CAPTURED: 'Problema no pagamento',
        deliveryStatus_IN_PROCESS: 'Pedido em processamento',
        deliveryStatus_READY: 'Pedido em processamento',
        deliveryStatus_DELIVERY_REJECTED: 'Entrega recusada',
        deliveryStatus_SHIPPED: 'Enviado',
        deliveryStatus_TAX_NOT_COMMITTED: 'Problema com impostos',
        deliveryStatus_CANCELLED: 'Cancelado',
        statusDisplay_pending: {
          approval: 'Aguardando aprovação',
          merchant: {
            approval: 'Aguardando aprovação da loja',
          },
        },
        statusDisplay_merchant: {
          approved: 'Aprovado pela loja',
          rejected: 'Recusado pela loja',
        },
        statusDisplay_assigned: {
          admin: 'Encaminhado ao administrador',
        },
        consignmentTracking: {
          action: 'Rastrear pacote',
          dialog: {
            header: 'Rastreamento',
            shipped: 'Enviado',
            estimate: 'Previsão de entrega',
            carrier: 'Transportadora',
            trackingId: 'Código de rastreamento',
            noTracking:
              'O pacote ainda não saiu do centro de distribuição. O rastreamento fica disponível depois do envio.',
            loadingHeader: 'Rastreamento da remessa',
          },
        },
        cancellationAndReturn: {
          returnAction: 'Solicitar devolução',
          cancelAction: 'Cancelar itens',
          item: 'Item',
          itemPrice: 'Preço unitário',
          quantity: 'Quantidade máx.',
          returnQty: 'Quantidade a devolver',
          cancelQty: 'Quantidade a cancelar',
          setAll: 'Usar a quantidade máxima em todos os itens',
          totalPrice: 'Total',
          submit: 'Enviar solicitação',
          submitDescription:
            'Enviar solicitação. Os itens desta página serão incluídos na solicitação de cancelamento.',
          returnSuccess: 'Sua solicitação de devolução ({{rma}}) foi enviada',
          cancelSuccess:
            'Sua solicitação de cancelamento foi enviada (o pedido original {{orderCode}} será atualizado)',
        },
        cancelReplenishment: {
          title: 'Cancelar reposição',
          description: 'Cancelar os próximos pedidos de reposição?',
          accept: 'Sim',
          reject: 'Não',
          cancelSuccess:
            'O pedido de reposição nº {{replenishmentOrderCode}} foi cancelado',
        },
      },
      orderHistory: {
        orderHistory: 'Histórico de pedidos',
        orderId: 'Pedido nº',
        emptyPurchaseOrderId: 'Nenhuma',
        date: 'Data',
        status: 'Situação',
        PONumber: 'Nº da ordem de compra',
        total: 'Total',
        noOrders: 'Você ainda não fez nenhum pedido com esta conta.',
        noReplenishmentOrders:
          'Você ainda não tem pedidos de reposição nesta conta.',
        startShopping: 'Começar a comprar',
        sortBy: 'Ordenar por',
        sortOrders: 'Ordenar pedidos',
        replenishmentOrderHistory: 'Histórico de pedidos de reposição',
        replenishmentOrderId: 'Reposição nº',
        purchaseOrderNumber: 'Ordem de compra nº',
        costCenter: 'Centro de custo',
        startOn: 'Início em',
        frequency: 'Frequência',
        nextOrderDate: 'Data do próximo pedido',
        cancel: 'Cancelar',
        cancelled: 'Cancelado',
        replenishmentHistory: 'Histórico de reposições',
        notFound: 'Nenhum pedido encontrado',
        actions: 'Ações',
      },
      AccountOrderHistoryTabContainer: {
        tabs: {
          AccountOrderHistoryComponent: 'TODOS OS PEDIDOS ({{param}})',
          OrderReturnRequestListComponent: 'DEVOLUÇÕES ({{param}})',
        },
        tabPanelContainerRegion: 'Abas com detalhes do histórico de pedidos',
        tabPanelContainerRegionGroup:
          'Grupo com detalhes do histórico de pedidos',
      },
      returnRequestList: {
        returnRequestId: 'Devolução nº',
        orderId: 'Pedido nº',
        date: 'Data de criação',
        status: 'Situação',
        sortBy: 'Ordenar por',
        sortReturns: 'Ordenar devoluções',
        statusDisplay_APPROVAL_PENDING: 'Aguardando aprovação',
        statusDisplay_CANCELED: 'Cancelada',
        statusDisplay_CANCELLING: 'Em cancelamento',
        statusDisplay_WAIT: 'Aguardando',
        statusDisplay_RECEIVED: 'Recebida',
        statusDisplay_RECEIVING: 'Em recebimento',
        statusDisplay_APPROVING: 'Em aprovação',
        statusDisplay_REVERSING_PAYMENT: 'Estornando pagamento',
        statusDisplay_PAYMENT_REVERSED: 'Pagamento estornado',
        statusDisplay_PAYMENT_REVERSAL_FAILED: 'Falha no estorno do pagamento',
        statusDisplay_REVERSING_TAX: 'Estornando impostos',
        statusDisplay_TAX_REVERSED: 'Impostos estornados',
        statusDisplay_TAX_REVERSAL_FAILED: 'Falha no estorno dos impostos',
        statusDisplay_COMPLETED: 'Concluída',
      },
      returnRequest: {
        returnRequestId: 'Solicitação de devolução nº',
        orderCode: 'Do pedido nº',
        status: 'Situação da devolução',
        cancel: 'Cancelar solicitação de devolução',
        item: 'Descrição',
        itemPrice: 'Preço unitário',
        returnQty: 'Quantidade a devolver',
        total: 'Total',
        summary: 'Totais da devolução',
        subtotal: 'Subtotal',
        deliveryCode: 'Frete',
        estimatedRefund: 'Reembolso estimado',
        note: 'Os totais são estimados e podem não incluir impostos ou outras taxas.',
        cancelSuccess: 'Sua solicitação de devolução ({{rma}}) foi cancelada',
        caption: 'Itens do pedido.',
      },
      reorder: {
        button: 'Comprar de novo',
        dialog: {
          reorderProducts: 'Comprar os produtos de novo',
          messages: {
            reviewConfiguration:
              'Ocorreu um erro na configuração de "{{ productCode}}".',
            lowStock:
              'A quantidade de {{ productName }} foi reduzida de {{ quantity }} para {{ quantityAdded }}.',
            noStock: '{{ productName }} está esgotado no momento.',
            pricingError: 'Problema no preço de "{{ productCode }}".',
            unresolvableIssues:
              'Problema não identificado com "{{ productCode }}".',
            success: 'Produtos adicionados ao carrinho',
          },
          areYouSureToReplaceCart:
            'Os itens do carrinho atual serão substituídos pelos novos. Quer continuar?',
          cancel: 'Cancelar',
          continue: 'Continuar',
        },
      },
    },
    wishlist: {
      wishlist: {
        empty: 'Sua lista de desejos ainda está vazia',
        itemRemoved: 'O item selecionado foi removido.',
        caption: 'Itens da lista de desejos.',
      },
    },
    savedCart: {
      savedCartDetails: {
        cartName: 'Nome',
        cartDescription: 'Descrição',
        cartId: 'Código',
        dateSaved: 'Salvo em',
        items: 'Itens',
        quantity: 'Quantidade',
        total: 'Total',
        editSavedCart: 'Editar carrinho salvo',
        deleteSavedCart: 'Excluir carrinho salvo',
        addSelectedItems: 'Adicionar itens selecionados',
        emptyCartItems: 'Este carrinho não tem itens',
      },
      savedCartList: {
        savedCarts: 'Carrinhos salvos ({{count}})',
        cartName: 'Nome',
        cartId: 'Código',
        dateSaved: 'Salvo em',
        cartDescription: 'Descrição',
        quantity: 'Qtd',
        quantityFull: 'Quantidade',
        total: 'Total',
        actions: 'Ações',
        makeCartActive: 'Usar este carrinho',
        notFound: 'Nenhum carrinho salvo encontrado',
        swapCartNoActiveCart:
          'O carrinho {{cartName}} agora é o carrinho atual.',
        swapCartWithActiveCart:
          'O carrinho {{cartName}} agora é o carrinho atual. Seus itens anteriores foram salvos no carrinho {{previousCartName}}.',
      },
      savedCartDialog: {
        followingCartDelete: 'Este carrinho salvo será excluído',
        followingCartRestore:
          'Este carrinho salvo será restaurado como carrinho atual',
        deleteCartSuccess: 'Carrinho excluído',
        editCartSuccess: 'Carrinho editado',
        editSavedCart: 'Editar carrinho salvo',
        deleteSavedCart: 'Excluir carrinho salvo',
        restoreSavedCart: 'Restaurar carrinho salvo',
        id: 'Código',
        keepCopySavedCart:
          'Manter uma cópia deste carrinho na lista de carrinhos salvos',
        defaultCloneCartName: 'Cópia de {{name}}',
        nameOfCloneCart: 'Nome da cópia do carrinho',
      },
    },
    importExport: {
      importEntriesDialog: {
        importProductsNewSavedCartSubtitle:
          'Adicione produtos importando um arquivo .CSV e crie um novo carrinho salvo.',
        importProductFileDetails:
          'O arquivo de texto deve ter a lista de produtos com as colunas obrigatórias separadas por vírgula: código e quantidade.',
        selectedSuccessfully: 'Arquivo CSV selecionado.',
        savedCartName: 'Nome do carrinho salvo',
        savedCartDescription: 'Descrição do carrinho salvo',
        optional: 'opcional',
        charactersLeft: 'caracteres restantes: {{count}}',
        summary: {
          info: 'Não feche nem atualize esta janela enquanto os produtos são importados.',
          loadedToCart:
            'Os produtos foram carregados no carrinho {{ cartName }}',
          loaded: 'Os produtos foram carregados',
          loading: 'Processando os produtos... ({{ count }}/{{ total }})',
          successes:
            '{{ successesCount }} de {{ total }} produtos foram importados.',
          warning: '{{ count }} produto não foi importado por completo.',
          warning_other:
            '{{ count }} produtos não foram importados por completo.',
          error: '{{ count }} produto não foi importado.',
          error_other: '{{ count }} produtos não foram importados.',
          messages: {
            unknownIdentifier:
              'O código de produto "{{ productCode}}" não existe.',
            lowStock:
              'A quantidade de {{ productName }} foi reduzida de {{ quantity }} para {{ quantityAdded }}.',
            noStock: '{{ productName }} está esgotado no momento.',
            unknownError: 'Problema não identificado com "{{ productCode }}".',
            limitExceeded:
              'Não foi possível adicionar "{{ productCode }}". Limite excedido.',
          },
          show: 'Mostrar',
          hide: 'Ocultar',
        },
      },
    },
    pickupInStore: {
      deliveryPointOfServiceDetails: {
        statusDisplay_cancelled: 'Cancelado',
        statusDisplay_cancelling: 'Cancelamento pendente',
        statusDisplay_completed: 'Concluído',
        statusDisplay_created: 'Criado',
        statusDisplay_error: 'Pendente',
        statusDisplay_Error: 'Pendente',
        statusDisplay_processing: 'Pendente',
        statusDisplay_open: 'Aberto',
        statusDisplay_pending: {
          approval: 'Aguardando aprovação',
          merchant: {
            approval: 'Aguardando aprovação da loja',
          },
        },
        statusDisplay_approved: 'Aprovado',
        statusDisplay_rejected: 'Recusado',
        statusDisplay_merchant: {
          approved: 'Aprovado pela loja',
          rejected: 'Recusado pela loja',
        },
        statusDisplay_assigned: {
          admin: 'Encaminhado ao administrador',
        },
      },
    },
    customerTicketing: {
      customerTicketing: {
        mainLogoLabel: 'SAP',
        mainTitle: 'Atendimento',
        ticketId: 'Código',
        createdOn: 'Criada em',
        changedOn: 'Alterada em',
        status: 'Situação',
        message: 'Mensagem',
        cancel: 'Cancelar',
        submit: 'Enviar',
        uploadFile: 'Anexar arquivo',
        fileSizeLimit: 'Tamanho máximo do arquivo: {{count}} MB',
        maximumAttachment: 'Máximo de um anexo por mensagem',
        errorMessage: 'Algo deu errado.',
        charactersLeft: 'caracteres restantes: {{count}}',
        customerService: 'Atendimento',
      },
      customerTicketingList: {
        subject: 'Assunto',
        ticketCategory: 'Categoria',
        sortSubtitle: 'Ordenar por',
        sortOrders: 'Ordenar solicitações',
        noTickets: 'Você não tem nenhuma solicitação',
        requestTitle: 'Todas as solicitações',
        mobile: {
          ticketIdFull: 'Código da solicitação',
        },
      },
      createCustomerTicket: {
        createNewTicket: 'Adicionar',
        addNewRequest: 'Nova solicitação',
        addRequest: 'Nova solicitação',
        subject: 'Assunto',
        category: 'Categoria',
        associateTo: 'Vincular a',
        ticketCreated: 'Solicitação criada.',
        optionallySelectAssociatedObject: 'Selecione uma opção',
        selectCategory: 'Selecione a categoria',
      },
      customerTicketingDetails: {
        requestReopened: 'Solicitação reaberta.',
        requestClosed:
          'Solicitação encerrada. Você pode reabri-la se precisar.',
        reopenRequest: 'Reabrir solicitação',
        closeRequest: 'Encerrar solicitação',
        ticketNotFound: 'Solicitação não encontrada.',
      },
    },
  },
};
