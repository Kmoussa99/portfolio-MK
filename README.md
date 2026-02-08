# Page de présentation — Moussa Koné

Ce dépôt contient une page statique (HTML/CSS/JS) prête à être déployée sur **GitHub Pages** via **GitHub Actions**.

## Démarrage local

- Ouvrir `index.html` dans votre navigateur (double-clic)
- ou lancer un petit serveur (optionnel) :

```bash
python -m http.server 8080
```

Puis ouvrir http://localhost:8080

## Ajouter votre photo

1. Remplacez `assets/avatar.jpg` (ou ajoutez-la si vous le souhaitez)
2. Dans `index.html`, remplacez le bloc `.avatar` par une balise `<img>` :
   - recherchez `class="avatar"` et suivez le commentaire dans le fichier.

## Déploiement GitHub Pages (GitHub Actions)

1. Poussez ce repo sur GitHub
2. Dans **Settings → Pages**
   - *Build and deployment* : **GitHub Actions**
3. Le workflow `.github/workflows/deploy.yml` publie le site à chaque push sur `main`

## Personnalisation rapide

- Couleurs : `styles.css` (variables `--accent`, `--accent2`)
- Sections : `index.html`
- Menu : `index.html` + `script.js`
