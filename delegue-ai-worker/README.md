# Délégué Football · Vision IA

Ce dossier contient le relais sécurisé entre l’application GitHub Pages et l’API OpenAI.

## Important

La clé OpenAI ne doit jamais être mise dans \`index.html\`, GitHub ou \`wrangler.json\`. Cloudflare recommande les Secrets pour les clés API.

## Déploiement Cloudflare Worker

1. Créer un Worker Cloudflare nommé \`delegue-football-ai\`.
2. Mettre le contenu de \`src/index.js\` dans le Worker.
3. Dans **Settings → Variables and Secrets → Add**, créer un secret :
   - Nom : \`OPENAI_API_KEY\`
   - Valeur : votre clé API OpenAI
4. Déployer le Worker.
5. Copier son adresse, par exemple \`https://delegue-football-ai.<votre-compte>.workers.dev\`.
6. Dans \`delegue-ai-test/index.html\`, remplacer :
   \`https://REMPLACE-MOI.workers.dev/scan\`
   par :
   \`https://delegue-football-ai.<votre-compte>.workers.dev/scan\`

L’application ne transmet que la photo au Worker. La clé reste côté serveur.

Le Worker utilise l’API Responses avec une image en entrée et renvoie uniquement les joueurs et le staff structurés en JSON.
