package fr.synxio.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComingSoonScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Prochainement", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Fonctionnalités à venir",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "L'application Synxio est en constante évolution ! Voici quelques-unes des améliorations sur lesquelles nous travaillons actuellement.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }



            item {
                FeatureCard(
                    icon = Icons.Rounded.Groups,
                    title = "Mode Soirée (Party Mode)",
                    description = "Permettez à vos amis connectés sur le même Wi-Fi de voter pour les prochains morceaux de la file d'attente !",
                    progress = 0.15f,
                    status = "À l'étude"
                )
            }
            
            item {
                FeatureCard(
                    icon = Icons.Rounded.CloudSync,
                    title = "Synchronisation Cloud Automatique",
                    description = "Sauvegardez vos playlists, vos favoris et vos statistiques de lecture pour les retrouver sur tous vos appareils.",
                    progress = 0.2f,
                    status = "À l'étude"
                )
            }

            item {
                FeatureCard(
                    icon = Icons.Rounded.GraphicEq,
                    title = "Crossfade & Gapless",
                    description = "Un fondu enchaîné ultra-fluide entre les morceaux et une lecture sans blanc pour les albums live.",
                    progress = 0.4f,
                    status = "En développement"
                )
            }

            item {
                FeatureCard(
                    icon = Icons.Rounded.AutoAwesome,
                    title = "Recommandations IA",
                    description = "Des mixes et des suggestions encore plus intelligents basés sur vos habitudes d'écoute grâce à l'IA embarquée.",
                    progress = 0.1f,
                    status = "À l'étude"
                )
            }

            item {
                FeatureCard(
                    icon = Icons.Rounded.DirectionsCar,
                    title = "Support Android Auto Avancé",
                    description = "Une interface Android Auto complètement repensée avec navigation complète de votre bibliothèque.",
                    progress = 0.3f,
                    status = "Planifié"
                )
            }

            item {
                FeatureCard(
                    icon = Icons.Rounded.Lyrics,
                    title = "Éditeur de Paroles Synchronisées",
                    description = "Créez et éditez directement vos propres paroles synchronisées (.lrc) depuis l'application.",
                    progress = 0.5f,
                    status = "En développement"
                )
            }
        }
    }
}

@Composable
private fun FeatureCard(
    icon: ImageVector,
    title: String,
    description: String,
    progress: Float,
    status: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .width(100.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )
            }
        }
    }
}
