# Lorenz Chaos — fond d'écran Wallpaper Engine

Attracteur de Lorenz en 3D temps réel (WebGL 2) : ~900 trajectoires chaotiques avec traînées 3D,
bloom HDR, aberration chromatique, caméra en orbite lente, parallaxe souris et réaction à l'audio
(les basses font pulser le halo).

En fond, un ciel profond calculé en direct : Voie lactée avec nuages d'étoiles et poussière sombre,
reste de supernova en filaments, nébuleuse à émission aux couleurs de la palette, et des milliers
d'étoiles nettes (couleurs selon leur température, quelques galaxies lointaines). Le ciel lointain est
calculé à part, lentement, par bandes : il coûte très peu à chaque image.

## Installation

1. Copier ce dossier dans `…/steamapps/common/wallpaper_engine/projects/myprojects/lorenz-chaos/`
2. Ouvrir Wallpaper Engine : le fond apparaît dans « Installés ».

Autre méthode : Wallpaper Engine → *Ouvrir un fond d'écran* → *Ouvrir depuis un fichier* → `index.html`.

La souris fait pivoter l'attracteur comme dans la version d'origine : horizontalement pour le tourner,
verticalement pour l'incliner (vue de dessus, de profil, par la tranche). Sa face avant suit le curseur. Pour ça, activer *Paramètres → Général → Interaction souris* dans Wallpaper Engine.

## Réglages

Tous modifiables dans le panneau du fond d'écran : palette (Spectre, Néon, Aurore, Braise, Glacier,
Or, couleur du thème), couleurs du ciel (comme les traînées, Hubble, Néon, Aurore, Braise, Glacier, Or,
Améthyste, Émeraude, noir et blanc ou couleur choisie), nombre de particules, longueur des traînées, vitesse, luminosité, halo,
lissage des courbes, qualité (auto / maximale), anticrénelage (analytique / MSAA), rotation automatique, balancement de la caméra et respiration de la forme
(désactivés par défaut), souris, étoiles, nébuleuse, sensibilité audio, départ en ruban.

Dans un navigateur, les mêmes réglages passent en paramètres d'URL, par ex.
`index.html?palette=3&count=1500&trail=250`.

## Performances

Mesuré sur Intel Iris Xe en 1920×1200 : **7 ms par image** contre 38,6 ms pour la première version, à rendu identique.

- Anticrénelage analytique : chaque traînée est un ruban de 2 px dont la couverture est calculée dans le shader, sans MSAA sur le buffer HDR.
- Une seule passe instanciée par traînée : chaque point de la spline n'est calculé qu'une fois.
- Historique des positions mis à jour par ligne contiguë.
- Limite d'images/s de Wallpaper Engine respectée sans dérive, pause automatique.
- Qualité auto : la résolution ne baisse que si la cadence demandée n'est plus tenue.

## Version Android

Une vraie appli de fond d'écran animé (dossier `android/`) qui affiche la même page `index.html` :

- **doigt** : on fait tourner l'attracteur (sa face avant suit le doigt) ; lancé, il garde son élan puis ralentit,
  et revient seul à sa vue de départ après quelques secondes ;
- **gyroscope** : quand on tourne le téléphone, l'attracteur a de l'inertie, puis revient en place avec un léger rebond ;
- **secousse** : les particules sont projetées, puis rappelées comme par un élastique ;
- **écran d'accueil** : légère rotation quand on change de page, petit zoom au retour sur l'accueil.

L'appli reprend tous les réglages de la version PC (sauf l'audio) et ajoute ceux du téléphone, avec un aperçu en direct.
Elle ne demande aucune permission.

**Qualité automatique** : le profil (Économe, Équilibrée, Élevée, Maximale) est choisi d'après la puce graphique
(Adreno, Mali, Immortalis, Xclipse, PowerVR), la mémoire et le nombre de cœurs ; il baisse d'un cran tout seul si le
téléphone n'arrive pas à suivre. On peut aussi choisir un profil ou tout régler à la main (Perso).

**Batterie** : le fond ne tourne que lorsqu'il est visible ; 30 images par seconde au repos (24 en Économe), 60
seulement pendant qu'on le touche ou qu'on bouge le téléphone ; capteurs lus ~30 fois par seconde et coupés si les
réglages ne s'en servent pas ; sur les écrans 90–120 Hz, la page dort entre deux images. En économie d'énergie, ou
quand le téléphone chauffe, il passe en Économe.

L'APK est compilé par GitHub Actions (`.github/workflows/android.yml`) à chaque modification de `index.html`
ou du dossier `android/`. La clé de signature `android/lorenz.keystore` est publique exprès : elle sert seulement
à ce que chaque nouvelle version s'installe par-dessus la précédente.

Pour essayer la version téléphone dans un navigateur : `index.html?mobile=1`.
