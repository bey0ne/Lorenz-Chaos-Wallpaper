# Lorenz Chaos — fond d'écran Wallpaper Engine

Attracteur de Lorenz en 3D temps réel (WebGL 2) : ~900 trajectoires chaotiques avec traînées 3D,
bloom HDR, aberration chromatique, nébuleuse et étoiles en fond, caméra en orbite lente,
parallaxe souris et réaction à l'audio (les basses font pulser le halo).

## Installation

1. Copier ce dossier dans `…/steamapps/common/wallpaper_engine/projects/myprojects/lorenz-chaos/`
2. Ouvrir Wallpaper Engine : le fond apparaît dans « Installés ».

Autre méthode : Wallpaper Engine → *Ouvrir un fond d'écran* → *Ouvrir depuis un fichier* → `index.html`.

La souris fait pivoter l'attracteur comme dans la version d'origine : horizontalement pour le tourner,
verticalement pour l'incliner (vue de dessus, de profil, par la tranche). Pour ça, activer *Paramètres → Général → Interaction souris* dans Wallpaper Engine.

## Réglages

Tous modifiables dans le panneau du fond d'écran : palette (Spectre, Néon, Aurore, Braise, Glacier,
Or, couleur du thème), nombre de particules, longueur des traînées, vitesse, luminosité, halo,
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
