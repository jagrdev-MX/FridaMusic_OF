const privacyTranslations = {
  en: {
    'meta.title': 'FridaMusic Privacy Policy | Frida Labs',
    'meta.description': 'Official FridaMusic privacy policy: local data, permissions, external services, advertising, and user choices.',
    'meta.ogTitle': 'FridaMusic Privacy Policy',
    'meta.ogDescription': 'Verifiable information about data access, storage, and transmission in FridaMusic.',
    'nav.label': 'Main navigation',
    'nav.menu': 'Open navigation',
    'nav.home': 'Home',
    'nav.features': 'Features',
    'nav.downloads': 'Downloads',
    'nav.closedBeta': 'Closed beta access',
    'nav.support': 'Support',
    'nav.privacy': 'Privacy',
    'language.label': 'Language selector',
    'hero.kicker': 'Public document',
    'hero.title': 'Privacy Policy',
    'hero.lead': 'Information about FridaMusic for Android and the official web services, written from verifiable behavior in the repository.',
    'hero.effectiveLabel': 'Effective date',
    'hero.updatedLabel': 'Last updated',
    'hero.effectiveDate': 'July 21, 2026',
    'hero.updatedDate': 'August 22, 2026',
    'hero.back': 'Back to Support',
    'toc.title': 'Contents',
    'toc.label': 'Privacy policy contents',
    'toc.controller': 'Controller and scope',
    'toc.summary': 'Data summary',
    'toc.library': 'Local library',
    'toc.storage': 'Local storage',
    'toc.network': 'External transmissions',
    'toc.ads': 'Advertising',
    'toc.permissions': 'Permissions',
    'toc.web': 'Official website',
    'toc.security': 'Security and retention',
    'toc.choices': 'User choices',
    'toc.children': 'Children',
    'toc.contact': 'Contact and changes',
    'controller.title': '1. Controller and scope',
    'controller.p1': 'FridaMusic is an Android music application published under the public Frida Labs identity. The repository identifies jagrdev-MX as its technical owner.',
    'controller.p2': 'This policy covers the application identified as com.jagr.fridamusic and the official frida-music-of.vercel.app website. It does not cover forks or third-party applications.',
    'controller.p3': 'The audited repository contains no account creation, authentication, Google Sign-In, Firebase, or first-party user profile system.',
    'summary.title': '2. Summary of verified data',
    'summary.tableLabel': 'Data processing summary',
    'summary.data': 'Data',
    'summary.source': 'Source',
    'summary.destination': 'Verified destination',
    'summary.purpose': 'Purpose',
    'summary.localData': 'Audio metadata, playlists, history, queue, settings, and local lyrics',
    'summary.localSource': 'Device and user actions',
    'summary.localDestination': 'Private app storage; some data may enter Android Auto Backup',
    'summary.localPurpose': 'Library, playback, and local personalization',
    'summary.queryData': 'Searches and song metadata',
    'summary.querySource': 'Entered text and local library',
    'summary.queryDestination': 'Google/YouTube, Apple iTunes Search, LRCLIB, and lyrics.ovh',
    'summary.queryPurpose': 'Find content, suggestions, artwork, and lyrics',
    'summary.adsData': 'Data the advertising SDK may handle automatically',
    'summary.adsSource': 'Device and app interaction',
    'summary.adsDestination': 'Google Mobile Ads',
    'summary.adsPurpose': 'Advertising, SDK analytics, and fraud prevention',
    'library.title': '3. Local music library',
    'library.p1': 'With the user’s permission, FridaMusic queries MediaStore to read the identifier, title, artist, duration, path, filename, album, album identifier, and date added for audio files. It also uses content and artwork URIs to play and display the library.',
    'library.p2': 'The audited code plays files from the device and does not upload the bytes of a local audio file to a first-party server. However, title, artist, album, or duration may be sent to external providers when looking up artwork or lyrics.',
    'library.p3': 'The application can also read embedded lyrics or lyric files located next to the audio file. Manual lyric edits are stored in the app’s private preferences.',
    'storage.title': '4. Data stored on the device',
    'storage.p1': 'Room stores playlists and playback history. SharedPreferences stores visual and playback settings, search history, followed artists, excluded songs, excluded folders, last playback, queue, local or cached lyrics, and custom artwork URIs.',
    'storage.p2': 'The app keeps local caches: up to 1 GB for remote audio with least-recently-used eviction and up to 100 MB for artwork. Temporary in-memory caches also exist for searches, suggestions, recommendations, streams, and lyric results.',
    'storage.p3': 'The manifest enables Android Auto Backup and the project rules do not exclude specific categories. Android may therefore back up eligible app data to the user’s backup account or transfer it between devices, depending on the Android version and settings. Standard Auto Backup excludes cache directories.',
    'network.title': '5. Data transmitted off the device',
    'network.search': 'Searches and suggestion requests are sent to Google Suggest, YouTube, and YouTube Music/InnerTube.',
    'network.streams': 'Video identifiers or URLs are used with YouTube and NewPipe Extractor to obtain metadata, audio streams, and subtitles.',
    'network.artwork': 'Title and artist are turned into an Apple iTunes Search query when remote artwork is needed.',
    'network.lyrics': 'Title, artist, album, and, when available, duration are sent to LRCLIB; title and artist may also be sent to lyrics.ovh. A subtitle track may be requested for YouTube content.',
    'network.p2': 'These requests take place to perform user-requested features or complete the playback experience. Providers also receive technical data inherent to a network connection. Their retention practices are governed by their own policies and are not present in this repository.',
    'ads.title': '6. Advertising and consent',
    'ads.p1': 'The GMS variant integrates Google Mobile Ads 25.4.0, and the audited code initializes it from the interstitial ad manager. This checkout contains no verifiable Google User Messaging Platform (UMP) dependency or consent-flow call. The merged manifest includes advertising ID and Android AdServices permissions contributed by the SDK.',
    'ads.p2': 'Google documentation for the Mobile Ads SDK family states that the SDK may automatically handle IP address, product interactions, diagnostics, and device or account identifiers for advertising, analytics, and fraud prevention. The owner must validate those disclosures against the distributed GMS artifact before completing Data safety.',
    'ads.todoTitle': 'Owner TODO',
    'ads.todo': 'Validate consent requirements and Data safety disclosures against the distributed GMS artifact before publishing. No UMP dependency or permanent privacy-options entry point was found in this checkout.',
    'permissions.title': '7. Android permissions',
    'permissions.tableLabel': 'Android permissions',
    'permissions.permission': 'Permission',
    'permissions.use': 'Verified use',
    'permissions.audio': 'Read and organize MediaStore audio; the legacy permission is declared only through Android 12L.',
    'permissions.notifications': 'Show notifications and controls associated with playback.',
    'permissions.foreground': 'Keep perceptible audio playback running in the background through MediaSessionService.',
    'permissions.internet': 'Access searches, streams, lyrics, artwork, and advertising.',
    'permissions.media3': 'Added by Media3 to inspect network state and sustain playback when applicable.',
    'permissions.ads': 'Added by Google Mobile Ads 25.4.0 for advertising, attribution, and ad-topics features.',
    'permissions.vibrate': 'Declared in the manifest, but no vibration call was found in the audited Kotlin code.',
    'permissions.reorder': 'Added by androidx.test:core because a test dependency is declared as an implementation dependency; no functional use was found in the app.',
    'permissions.receiver': 'Internal signature-protected permission added by AndroidX Core for non-exported dynamic receivers.',
    'web.title': '8. Official website',
    'web.p1': 'The main website loads Google Fonts and Vercel Web Analytics and Speed Insights modules. It also stores the ES, EN, or PT preference in localStorage. This privacy page does not load Vercel analytics modules and can be read without signing in or accepting cookies.',
    'security.title': '9. Security, retention, and deletion',
    'security.p1': 'Endpoints written in the Android code use HTTPS, and local data is stored in standard private Android areas, except for audio files and artwork selected from shared storage. The repository implements no additional encryption for Room or SharedPreferences, so first-party encryption at rest is not claimed.',
    'security.p2': 'Caches have technical limits, but there is no general retention period for playlists, history, preferences, or saved lyrics. Users can clear searches, playlists, or lyrics through available features, disable last-playback saving, or remove all data through Android settings or by uninstalling the app. Although the DAO can clear playback history, no visible control invoking that operation was verified.',
    'security.p3': 'No FridaMusic account exists in the audited code, so there is no account-deletion process. Data handled by external providers or the web backend is subject to those operators’ controls and policies.',
    'choices.title': '10. User choices and rights',
    'choices.permissions': 'Grant or revoke audio and notification access in Android settings.',
    'choices.local': 'Clear search history, playlists, and edited lyrics through available options.',
    'choices.playback': 'Disable saving the last playback.',
    'choices.ads': 'Use Android advertising ID controls.',
    'choices.device': 'Clear app storage or uninstall the app to remove local data.',
    'choices.contact': 'Contact Frida Labs with questions about this policy or data under its verified control.',
    'children.title': '11. Children’s privacy',
    'children.p1': 'The repository does not contain the target-audience declaration configured in Google Play Console. This policy therefore does not claim that FridaMusic is or is not directed to children.',
    'children.todoTitle': 'Owner TODO',
    'children.todo': 'Confirm the declared audience, content rating, and whether the Families Policy applies before using this URL in production.',
    'contact.title': '12. Contact and changes',
    'contact.p1': 'For privacy questions or requests concerning data under FridaMusic’s control, write to the official email:',
    'contact.p2': 'This policy will be updated when the product’s verified features, permissions, SDKs, or practices change. The last-updated date will appear at the beginning of this page.',
    'contact.p3': 'Before publishing it on Google Play, Frida Labs must resolve the stated TODOs and verify that this text matches the distributed APK/AAB, the developer listing, and the Play Console Data safety section.',
    'footer.copy': '© 2026 Frida Labs MX. Made with passion for design.',
  },
  pt: {
    'meta.title': 'Política de privacidade do FridaMusic | Frida Labs',
    'meta.description': 'Política de privacidade oficial do FridaMusic: dados locais, permissões, serviços externos, publicidade e opções do usuário.',
    'meta.ogTitle': 'Política de privacidade do FridaMusic',
    'meta.ogDescription': 'Informações verificáveis sobre acesso, armazenamento e transmissão de dados no FridaMusic.',
    'nav.label': 'Navegação principal',
    'nav.menu': 'Abrir navegação',
    'nav.home': 'Início',
    'nav.features': 'Características',
    'nav.downloads': 'Downloads',
    'nav.closedBeta': 'Acesso beta fechado',
    'nav.support': 'Suporte',
    'nav.privacy': 'Privacidade',
    'language.label': 'Seletor de idioma',
    'hero.kicker': 'Documento público',
    'hero.title': 'Política de privacidade',
    'hero.lead': 'Informações sobre o FridaMusic para Android e os serviços web oficiais, redigidas a partir do comportamento verificável no repositório.',
    'hero.effectiveLabel': 'Entrada em vigor',
    'hero.updatedLabel': 'Última atualização',
    'hero.effectiveDate': '21 de julho de 2026',
    'hero.updatedDate': '22 de agosto de 2026',
    'hero.back': 'Voltar ao Suporte',
    'toc.title': 'Índice',
    'toc.label': 'Índice da política de privacidade',
    'toc.controller': 'Responsável e escopo',
    'toc.summary': 'Resumo dos dados',
    'toc.library': 'Biblioteca local',
    'toc.storage': 'Armazenamento local',
    'toc.network': 'Transmissões externas',
    'toc.ads': 'Publicidade',
    'toc.permissions': 'Permissões',
    'toc.web': 'Site oficial',
    'toc.security': 'Segurança e retenção',
    'toc.choices': 'Opções do usuário',
    'toc.children': 'Crianças',
    'toc.contact': 'Contato e alterações',
    'controller.title': '1. Responsável e escopo',
    'controller.p1': 'FridaMusic é um aplicativo de música para Android publicado sob a identidade pública da Frida Labs. O repositório identifica jagrdev-MX como owner técnico.',
    'controller.p2': 'Esta política abrange o aplicativo identificado como com.jagr.fridamusic e o site oficial frida-music-of.vercel.app. Não abrange forks nem aplicativos de terceiros.',
    'controller.p3': 'O repositório auditado não contém criação de contas, autenticação, Google Sign-In, Firebase nem um sistema próprio de perfis de usuário.',
    'summary.title': '2. Resumo dos dados verificados',
    'summary.tableLabel': 'Resumo do tratamento de dados',
    'summary.data': 'Dados',
    'summary.source': 'Origem',
    'summary.destination': 'Destino verificado',
    'summary.purpose': 'Finalidade',
    'summary.localData': 'Metadados de áudio, playlists, histórico, fila, ajustes e letras locais',
    'summary.localSource': 'Dispositivo e ações do usuário',
    'summary.localDestination': 'Armazenamento privado do app; parte pode entrar no Android Auto Backup',
    'summary.localPurpose': 'Biblioteca, reprodução e personalização local',
    'summary.queryData': 'Pesquisas e metadados de músicas',
    'summary.querySource': 'Texto inserido e biblioteca local',
    'summary.queryDestination': 'Google/YouTube, Apple iTunes Search, LRCLIB e lyrics.ovh',
    'summary.queryPurpose': 'Encontrar conteúdo, sugestões, capas e letras',
    'summary.adsData': 'Dados que o SDK de publicidade pode tratar automaticamente',
    'summary.adsSource': 'Dispositivo e interação com o app',
    'summary.adsDestination': 'Google Mobile Ads',
    'summary.adsPurpose': 'Publicidade, análise do SDK e prevenção de fraude',
    'library.title': '3. Biblioteca musical local',
    'library.p1': 'Com a permissão do usuário, o FridaMusic consulta o MediaStore para ler identificador, título, artista, duração, caminho, nome do arquivo, álbum, identificador do álbum e data de adição dos arquivos de áudio. Também usa URI de conteúdo e capa para reproduzir e exibir a biblioteca.',
    'library.p2': 'O código auditado reproduz os arquivos a partir do dispositivo e não envia os bytes de um arquivo de áudio local para um servidor próprio. No entanto, título, artista, álbum ou duração podem ser enviados a provedores externos ao buscar capas ou letras.',
    'library.p3': 'O aplicativo também pode ler letras incorporadas ou arquivos de letras localizados ao lado do áudio. As edições manuais de letras são armazenadas nas preferências privadas do app.',
    'storage.title': '4. Dados armazenados no dispositivo',
    'storage.p1': 'Room armazena playlists e histórico de reprodução. SharedPreferences armazena ajustes visuais e de reprodução, histórico de pesquisa, artistas seguidos, músicas excluídas, pastas excluídas, última reprodução, fila, letras locais ou em cache e URI de capas personalizadas.',
    'storage.p2': 'O app mantém caches locais: até 1 GB para áudio remoto com remoção pelo uso menos recente e até 100 MB para capas. Também existem caches temporários em memória para pesquisas, sugestões, recomendações, streams e resultados de letras.',
    'storage.p3': 'O manifesto habilita o Android Auto Backup e as regras do projeto não excluem categorias específicas. Portanto, o Android pode fazer backup de dados elegíveis do app na conta de backup do usuário ou transferi-los entre dispositivos, conforme a versão e as configurações do Android. As pastas de cache ficam fora do Auto Backup padrão.',
    'network.title': '5. Dados transmitidos para fora do dispositivo',
    'network.search': 'Pesquisas e solicitações de sugestões são enviadas ao Google Suggest, YouTube e YouTube Music/InnerTube.',
    'network.streams': 'Identificadores ou URL de vídeos são usados com YouTube e NewPipe Extractor para obter metadados, streams de áudio e legendas.',
    'network.artwork': 'Título e artista são convertidos em uma consulta ao Apple iTunes Search quando é necessária uma capa remota.',
    'network.lyrics': 'Título, artista, álbum e, quando disponível, duração são enviados ao LRCLIB; título e artista também podem ser enviados ao lyrics.ovh. Uma faixa de legendas pode ser solicitada para conteúdo do YouTube.',
    'network.p2': 'Essas solicitações ocorrem para executar funções pedidas pelo usuário ou completar a experiência de reprodução. Os provedores também recebem dados técnicos inerentes a uma conexão de rede. As práticas de retenção deles são regidas por suas próprias políticas e não aparecem neste repositório.',
    'ads.title': '6. Publicidade e consentimento',
    'ads.p1': 'A variante GMS integra o Google Mobile Ads 25.4.0, e o código auditado o inicializa a partir do gerenciador de anúncios interstitials. Este checkout não contém uma dependência nem uma chamada verificável do Google User Messaging Platform (UMP). O manifesto mesclado inclui permissões de ID de publicidade e Android AdServices adicionadas pelo SDK.',
    'ads.p2': 'A documentação do Google para a família Mobile Ads informa que o SDK pode tratar automaticamente endereço IP, interações com o produto, diagnósticos e identificadores do dispositivo ou da conta para publicidade, análise e prevenção de fraude. O owner deve validar essas declarações contra o artefato GMS distribuído antes de preencher a seção Segurança dos dados.',
    'ads.todoTitle': 'TODO do owner',
    'ads.todo': 'Validar os requisitos de consentimento e as declarações de Segurança dos dados contra o artefato GMS distribuído antes da publicação. Não foi encontrada uma dependência UMP nem uma entrada permanente de opções de privacidade neste checkout.',
    'permissions.title': '7. Permissões do Android',
    'permissions.tableLabel': 'Permissões do Android',
    'permissions.permission': 'Permissão',
    'permissions.use': 'Uso verificado',
    'permissions.audio': 'Ler e organizar áudio do MediaStore; a permissão antiga é declarada apenas até o Android 12L.',
    'permissions.notifications': 'Mostrar notificações e controles associados à reprodução.',
    'permissions.foreground': 'Manter a reprodução de áudio perceptível em segundo plano por meio do MediaSessionService.',
    'permissions.internet': 'Acessar pesquisas, streams, letras, capas e publicidade.',
    'permissions.media3': 'Adicionadas pelo Media3 para consultar o estado da rede e sustentar a reprodução quando aplicável.',
    'permissions.ads': 'Adicionadas pelo Google Mobile Ads 25.4.0 para funções de publicidade, atribuição e tópicos de anúncios.',
    'permissions.vibrate': 'Está declarada no manifesto, mas nenhuma chamada de vibração foi encontrada no código Kotlin auditado.',
    'permissions.reorder': 'Adicionada pelo androidx.test:core porque uma dependência de testes está declarada como implementação; nenhum uso funcional foi encontrado no app.',
    'permissions.receiver': 'Permissão interna protegida por assinatura, adicionada pelo AndroidX Core para receptores dinâmicos não exportados.',
    'web.title': '8. Site oficial',
    'web.p1': 'O site principal carrega Google Fonts e módulos do Vercel Web Analytics e Speed Insights. Também armazena a preferência ES, EN ou PT no localStorage. Esta página de privacidade não carrega os módulos de análise da Vercel e pode ser lida sem login nem aceitação de cookies.',
    'security.title': '9. Segurança, retenção e exclusão',
    'security.p1': 'Os endpoints escritos no código Android usam HTTPS, e os dados locais são armazenados nas áreas privadas padrão do Android, exceto arquivos de áudio e capas selecionados do armazenamento compartilhado. O repositório não implementa criptografia adicional para Room ou SharedPreferences; portanto, não se afirma criptografia própria em repouso.',
    'security.p2': 'Os caches têm limites técnicos, mas não existe um prazo geral de retenção para playlists, histórico, preferências ou letras salvas. O usuário pode apagar pesquisas, playlists ou letras pelas funções disponíveis, desativar o salvamento da última reprodução ou remover todos os dados nos ajustes do Android ou desinstalando o app. Embora o DAO possa limpar o histórico de reprodução, nenhum controle visível que invoque essa operação foi verificado.',
    'security.p3': 'Não existe uma conta FridaMusic no código auditado; portanto, não há um processo de exclusão de conta. Dados tratados por provedores externos ou pelo backend web estão sujeitos aos controles e políticas desses operadores.',
    'choices.title': '10. Opções e direitos do usuário',
    'choices.permissions': 'Conceder ou revogar acesso ao áudio e às notificações nos ajustes do Android.',
    'choices.local': 'Apagar o histórico de pesquisa, playlists e letras editadas por meio das opções disponíveis.',
    'choices.playback': 'Desativar o salvamento da última reprodução.',
    'choices.ads': 'Usar os controles do ID de publicidade do Android.',
    'choices.device': 'Limpar o armazenamento do app ou desinstalá-lo para remover os dados locais.',
    'choices.contact': 'Contatar a Frida Labs com dúvidas sobre esta política ou dados sob seu controle verificado.',
    'children.title': '11. Privacidade de crianças',
    'children.p1': 'O repositório não contém a declaração de público-alvo configurada no Google Play Console. Por isso, esta política não afirma que o FridaMusic seja ou não direcionado a crianças.',
    'children.todoTitle': 'TODO do owner',
    'children.todo': 'Confirmar o público declarado, a classificação de conteúdo e se a Política para Famílias se aplica antes de usar esta URL em produção.',
    'contact.title': '12. Contato e alterações',
    'contact.p1': 'Para dúvidas sobre privacidade ou solicitações relativas a dados sob o controle do FridaMusic, escreva para o e-mail oficial:',
    'contact.p2': 'Esta política será atualizada quando as funções, permissões, SDKs ou práticas verificadas do produto mudarem. A data da última atualização aparecerá no início desta página.',
    'contact.p3': 'Antes de publicá-la no Google Play, a Frida Labs deve resolver os TODO indicados e verificar se o texto corresponde ao APK/AAB distribuído, à ficha do desenvolvedor e à seção Segurança dos dados do Play Console.',
    'footer.copy': '© 2026 Frida Labs MX. Feito com paixão por design.',
  },
};

const defaultText = new Map();
const defaultContent = new Map();
let currentLanguage = localStorage.getItem('fridaMusicLanguage') || 'es';

function rememberSpanishContent() {
  document.querySelectorAll('[data-i18n]').forEach((element) => {
    defaultText.set(element, element.textContent.trim());
  });

  document.querySelectorAll('[data-i18n-content]').forEach((element) => {
    defaultContent.set(element, element.getAttribute('content') || '');
  });
}

function translate(key, fallback) {
  if (currentLanguage === 'es') return fallback;
  return privacyTranslations[currentLanguage]?.[key] || fallback;
}

function applyTranslations(language) {
  currentLanguage = language === 'en' || language === 'pt' ? language : 'es';
  document.documentElement.lang = currentLanguage;
  localStorage.setItem('fridaMusicLanguage', currentLanguage);

  document.querySelectorAll('[data-i18n]').forEach((element) => {
    element.textContent = translate(element.dataset.i18n, defaultText.get(element) || '');
  });

  document.querySelectorAll('[data-i18n-content]').forEach((element) => {
    element.setAttribute('content', translate(element.dataset.i18nContent, defaultContent.get(element) || ''));
  });

  document.querySelectorAll('[data-i18n-aria-label]').forEach((element) => {
    const fallback = element.dataset.defaultAriaLabel || element.getAttribute('aria-label') || '';
    element.dataset.defaultAriaLabel = fallback;
    element.setAttribute('aria-label', translate(element.dataset.i18nAriaLabel, fallback));
  });

  document.querySelectorAll('.language-option').forEach((button) => {
    button.setAttribute('aria-pressed', String(button.dataset.lang === currentLanguage));
  });
}

function setupNavbar() {
  const toggle = document.querySelector('.nav-toggle');
  const navigation = document.getElementById('site-nav');
  if (!toggle || !navigation) return;

  const closeNavigation = () => {
    navigation.classList.remove('is-open');
    toggle.setAttribute('aria-expanded', 'false');
  };

  toggle.addEventListener('click', () => {
    const isOpen = navigation.classList.toggle('is-open');
    toggle.setAttribute('aria-expanded', String(isOpen));
  });

  navigation.querySelectorAll('a').forEach((link) => link.addEventListener('click', closeNavigation));
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') closeNavigation();
  });
}

function setupLanguageSwitcher() {
  document.querySelectorAll('.language-option').forEach((button) => {
    button.addEventListener('click', () => applyTranslations(button.dataset.lang));
  });
}

function setupBackgroundCanvas() {
  const canvas = document.getElementById('bg-canvas');
  const context = canvas?.getContext('2d');
  if (!canvas || !context) return;

  const colors = ['#7c4dff', '#e040fb', '#40c4ff', '#1a0850', '#0a2060'];
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  let width = 0;
  let height = 0;
  let orbs = [];

  const rgb = (hex) => {
    const red = Number.parseInt(hex.slice(1, 3), 16);
    const green = Number.parseInt(hex.slice(3, 5), 16);
    const blue = Number.parseInt(hex.slice(5, 7), 16);
    return `${red},${green},${blue}`;
  };

  const createOrb = () => ({
    x: Math.random() * width,
    y: Math.random() * height,
    radius: 90 + Math.random() * 180,
    dx: (Math.random() - 0.5) * 0.5,
    dy: (Math.random() - 0.5) * 0.45,
    color: colors[Math.floor(Math.random() * colors.length)],
    alpha: 0.05 + Math.random() * 0.08,
  });

  const resize = () => {
    const pixelRatio = Math.min(window.devicePixelRatio || 1, 2);
    width = window.innerWidth;
    height = window.innerHeight;
    canvas.width = Math.floor(width * pixelRatio);
    canvas.height = Math.floor(height * pixelRatio);
    canvas.style.width = `${width}px`;
    canvas.style.height = `${height}px`;
    context.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0);
    orbs = Array.from({ length: 16 }, createOrb);
  };

  const draw = () => {
    context.clearRect(0, 0, width, height);
    context.fillStyle = 'rgba(5,3,18,.98)';
    context.fillRect(0, 0, width, height);

    orbs.forEach((orb) => {
      const gradient = context.createRadialGradient(orb.x, orb.y, 0, orb.x, orb.y, orb.radius);
      gradient.addColorStop(0, `rgba(${rgb(orb.color)},${orb.alpha})`);
      gradient.addColorStop(1, `rgba(${rgb(orb.color)},0)`);
      context.beginPath();
      context.arc(orb.x, orb.y, orb.radius, 0, Math.PI * 2);
      context.fillStyle = gradient;
      context.fill();

      if (!reduceMotion) {
        orb.x += orb.dx;
        orb.y += orb.dy;
        if (orb.x < -orb.radius) orb.x = width + orb.radius;
        if (orb.x > width + orb.radius) orb.x = -orb.radius;
        if (orb.y < -orb.radius) orb.y = height + orb.radius;
        if (orb.y > height + orb.radius) orb.y = -orb.radius;
      }
    });

    if (!reduceMotion) window.requestAnimationFrame(draw);
  };

  resize();
  draw();
  window.addEventListener('resize', () => {
    resize();
    if (reduceMotion) draw();
  });
}

document.addEventListener('DOMContentLoaded', () => {
  rememberSpanishContent();
  setupNavbar();
  setupLanguageSwitcher();
  setupBackgroundCanvas();
  applyTranslations(currentLanguage);
});
