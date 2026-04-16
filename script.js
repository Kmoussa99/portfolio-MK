(() => {
  // ─── Dynamic Year ───
  const year = document.getElementById('year');
  if (year) year.textContent = String(new Date().getFullYear());

  // ─── Mobile Nav Drawer ───
  const btn = document.getElementById('navbtn');
  const drawer = document.getElementById('navdrawer');

  if (btn && drawer) {
    const close = () => {
      drawer.hidden = true;
      btn.setAttribute('aria-expanded', 'false');
    };
    const open = () => {
      drawer.hidden = false;
      btn.setAttribute('aria-expanded', 'true');
    };

    btn.addEventListener('click', () => {
      btn.getAttribute('aria-expanded') === 'true' ? close() : open();
    });

    drawer.querySelectorAll('a').forEach(a => a.addEventListener('click', close));
    document.addEventListener('keydown', (e) => { if (e.key === 'Escape') close(); });
  }

  // ─── Smooth Scroll for anchor links ───
  document.querySelectorAll('a[href^="#"]').forEach(link => {
    link.addEventListener('click', (e) => {
      const target = document.querySelector(link.getAttribute('href'));
      if (target) {
        e.preventDefault();
        target.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }
    });
  });

  // ─── Scroll Reveal (Intersection Observer) ───
  const animatedElements = document.querySelectorAll('[data-animate]');

  if (animatedElements.length && 'IntersectionObserver' in window) {
    const revealObserver = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add('visible');
          revealObserver.unobserve(entry.target);
        }
      });
    }, {
      threshold: 0.12,
      rootMargin: '0px 0px -40px 0px'
    });

    animatedElements.forEach((el, index) => {
      el.style.transitionDelay = `${index * 0.08}s`;
      revealObserver.observe(el);
    });
  } else {
    // Fallback: show everything
    animatedElements.forEach(el => el.classList.add('visible'));
  }

  // ─── Active Nav Link on Scroll ───
  const sections = document.querySelectorAll('section[id]');
  const navLinks = document.querySelectorAll('.nav__link');

  if (sections.length && navLinks.length) {
    const navObserver = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          const id = entry.target.getAttribute('id');
          navLinks.forEach(link => {
            link.classList.toggle('active', link.getAttribute('href') === `#${id}`);
          });
        }
      });
    }, {
      threshold: 0.3,
      rootMargin: '-80px 0px -50% 0px'
    });

    sections.forEach(section => navObserver.observe(section));
  }

  // ─── Parallax Background Orbs on Mouse Move ───
  const orbs = document.querySelectorAll('.orb');
  if (orbs.length && window.matchMedia('(min-width: 768px)').matches) {
    let ticking = false;
    document.addEventListener('mousemove', (e) => {
      if (ticking) return;
      ticking = true;
      requestAnimationFrame(() => {
        const x = (e.clientX / window.innerWidth - 0.5) * 2;
        const y = (e.clientY / window.innerHeight - 0.5) * 2;
        orbs.forEach((orb, i) => {
          const speed = (i + 1) * 12;
          orb.style.transform = `translate(${x * speed}px, ${y * speed}px)`;
        });
        ticking = false;
      });
    });
  }

  // ─── Card tilt effect on hover ───
  const tiltCards = document.querySelectorAll('.profile-card, .bento__item');
  if (window.matchMedia('(min-width: 768px)').matches) {
    tiltCards.forEach(card => {
      card.addEventListener('mousemove', (e) => {
        const rect = card.getBoundingClientRect();
        const x = (e.clientX - rect.left) / rect.width - 0.5;
        const y = (e.clientY - rect.top) / rect.height - 0.5;
        card.style.transform = `perspective(800px) rotateY(${x * 5}deg) rotateX(${-y * 5}deg) translateY(-4px)`;
      });
      card.addEventListener('mouseleave', () => {
        card.style.transform = '';
      });
    });
  }
})();
