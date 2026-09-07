const betaTranslations = {
  en: {
    'meta.title': 'FridaMusic Closed Beta Access | Frida Labs',
    'meta.description': 'Request access to the FridaMusic closed beta and complete the official testing-program form.',
    'meta.ogTitle': 'FridaMusic Closed Beta Access',
    'meta.ogDescription': 'Request early access and help test upcoming FridaMusic versions.',
    'nav.label': 'Main navigation',
    'nav.menu': 'Open navigation',
    'nav.home': 'Home',
    'nav.features': 'Features',
    'nav.downloads': 'Downloads',
    'nav.closedBeta': 'Closed beta access',
    'nav.support': 'Support',
    'nav.privacy': 'Privacy',
    'language.label': 'Language selector',
    'beta.kicker': 'Testing program',
    'beta.title': 'Closed beta access',
    'beta.copy': 'Request early access to FridaMusic and help test upcoming versions before their public release.',
    'beta.back': 'Back to the main website',
    'beta.bannerAlt': 'FridaMusic closed beta program',
    'beta.status': 'Application required',
    'beta.infoTitle': 'How to request access',
    'beta.infoCopy': 'Access is managed through the official form shown on this page.',
    'beta.step1Title': 'Complete your details',
    'beta.step1Copy': 'Use the email for the Google Play account you will use to participate.',
    'beta.step2Title': 'Submit your application',
    'beta.step2Copy': 'Check that your information is correct before sending it.',
    'beta.step3Title': 'Wait for confirmation',
    'beta.step3Copy': 'The team will review the application and share the next steps if your access is enabled.',
    'beta.action': 'Fill out the form',
    'beta.note': 'Submitting the form does not activate access immediately or guarantee a place.',
    'beta.formKicker': 'Access application',
    'beta.formTitle': 'Closed beta form',
    'beta.formCopy': 'Complete your application here to finish the process without leaving FridaMusic.',
    'beta.formFrameTitle': 'FridaMusic closed beta access form',
    'footer.copy': '© 2026 Frida Labs MX. Made with passion for design.',
  },
  pt: {
    'meta.title': 'Acesso à beta fechada do FridaMusic | Frida Labs',
    'meta.description': 'Solicite acesso à beta fechada do FridaMusic e preencha o formulário oficial do programa de testes.',
    'meta.ogTitle': 'Acesso à beta fechada do FridaMusic',
    'meta.ogDescription': 'Solicite acesso antecipado e ajude a testar as próximas versões do FridaMusic.',
    'nav.label': 'Navegação principal',
    'nav.menu': 'Abrir navegação',
    'nav.home': 'Início',
    'nav.features': 'Características',
    'nav.downloads': 'Downloads',
    'nav.closedBeta': 'Acesso beta fechado',
    'nav.support': 'Suporte',
    'nav.privacy': 'Privacidade',
    'language.label': 'Seletor de idioma',
    'beta.kicker': 'Programa de testes',
    'beta.title': 'Acesso beta fechado',
    'beta.copy': 'Solicite acesso antecipado ao FridaMusic e ajude a testar as próximas versões antes do lançamento público.',
    'beta.back': 'Voltar ao site principal',
    'beta.bannerAlt': 'Programa beta fechado do FridaMusic',
    'beta.status': 'Inscrição mediante solicitação',
    'beta.infoTitle': 'Como solicitar acesso',
    'beta.infoCopy': 'O acesso é gerenciado pelo formulário oficial exibido nesta página.',
    'beta.step1Title': 'Preencha seus dados',
    'beta.step1Copy': 'Use o e-mail da conta do Google Play com a qual você participará.',
    'beta.step2Title': 'Envie a solicitação',
    'beta.step2Copy': 'Verifique se as informações estão corretas antes de enviar.',
    'beta.step3Title': 'Aguarde a confirmação',
    'beta.step3Copy': 'A equipe revisará a solicitação e informará os próximos passos se o seu acesso for habilitado.',
    'beta.action': 'Preencher formulário',
    'beta.note': 'O envio do formulário não ativa o acesso imediatamente nem garante uma vaga.',
    'beta.formKicker': 'Solicitação de acesso',
    'beta.formTitle': 'Formulário da beta fechada',
    'beta.formCopy': 'Preencha a solicitação aqui para concluir o processo sem sair do FridaMusic.',
    'beta.formFrameTitle': 'Formulário de acesso à beta fechada do FridaMusic',
    'footer.copy': '© 2026 Frida Labs MX. Feito com paixão por design.',
  },
};

const defaultText = new Map();
const defaultContent = new Map();
const defaultAlt = new Map();
const defaultTitle = new Map();
let currentLanguage = localStorage.getItem('fridaMusicLanguage') || 'es';

function rememberSpanishContent() {
  document.querySelectorAll('[data-i18n]').forEach((element) => {
    defaultText.set(element, element.textContent.trim());
  });

  document.querySelectorAll('[data-i18n-content]').forEach((element) => {
    defaultContent.set(element, element.getAttribute('content') || '');
  });

  document.querySelectorAll('[data-i18n-alt]').forEach((element) => {
    defaultAlt.set(element, element.getAttribute('alt') || '');
  });

  document.querySelectorAll('[data-i18n-title]').forEach((element) => {
    defaultTitle.set(element, element.getAttribute('title') || '');
  });
}

function translate(key, fallback) {
  if (currentLanguage === 'es') return fallback;
  return betaTranslations[currentLanguage]?.[key] || fallback;
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

  document.querySelectorAll('[data-i18n-alt]').forEach((element) => {
    element.setAttribute('alt', translate(element.dataset.i18nAlt, defaultAlt.get(element) || ''));
  });

  document.querySelectorAll('[data-i18n-title]').forEach((element) => {
    element.setAttribute('title', translate(element.dataset.i18nTitle, defaultTitle.get(element) || ''));
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
