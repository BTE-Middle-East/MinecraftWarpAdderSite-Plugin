package org.axolotlagatsuma.databaseconnector

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.sql.SQLException
import github.scarsz.discordsrv.DiscordSRV
import github.scarsz.discordsrv.dependencies.jda.api.EmbedBuilder
import java.awt.Color

class DatabaseConnector : JavaPlugin(), CommandExecutor {

    private lateinit var databaseManager: DatabaseManager

    override fun onEnable() {
        saveDefaultConfig()
        loadConfiguration()
        getCommand("db")?.setExecutor(this)
        server.scheduler.runTaskTimer(this, Runnable { databaseManager.copyFilesToDirectory() }, 0L, 1200L)
        logger.info("Plugin Enabled")
    }

    override fun onDisable() {
        databaseManager.close()
        logger.info("Plugin Disabled")
    }

    private fun loadConfiguration() {
        reloadConfig()
        databaseManager = DatabaseManager(
            config.getString("database.url")!!,
            config.getString("database.username")!!,
            config.getString("database.password")!!,
            config.getInt("database.poolSize"),
            config.getString("database.tableName")!!,
            config.getString("targetDirectory")!!,
            config.getString("discord.channelId")!!
        )
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (command.name.equals("db", ignoreCase = true)) {
            if (args.isNotEmpty() && args[0].equals("reload", ignoreCase = true)) {
                if (sender.hasPermission("databaseconnector.reload") || sender.hasPermission("databaseconnector.admin")) {
                    loadConfiguration()
                    sender.sendMessage("§aConfiguration reloaded successfully.")
                } else {
                    sender.sendMessage("§cYou do not have permission to execute this command.")
                }
                return true
            } else if (args.isNotEmpty() && args[0].equals("checknow", ignoreCase = true)) {
                if (sender.hasPermission("databaseconnector.checknow") || sender.hasPermission("databaseconnector.admin")) {
                    databaseManager.copyFilesToDirectory()
                    sender.sendMessage("§aFiles copied successfully.")
                } else {
                    sender.sendMessage("§cYou do not have permission to execute this command.")
                }
                return true
            }
        }
        sender.sendMessage("§cUsage: /$label <reload|checknow>")
        return false
    }

    class DatabaseManager(
        url: String,
        username: String,
        password: String,
        poolSize: Int,
        private val tableName: String,
        private val targetDirectory: String,
        private val discordChannelId: String
    ) {

        private val dataSource: HikariDataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = url
            this.username = username
            this.password = password
            maximumPoolSize = poolSize
        })

        private val processedFiles = mutableSetOf<String>()

        init {
            // Initialize processedFiles with existing files in the target directory
            val directory = File(targetDirectory)
            if (directory.exists() && directory.isDirectory) {
                directory.listFiles()?.forEach { file ->
                    if (file.isFile && file.extension == "yml") {
                        processedFiles.add(file.nameWithoutExtension)
                    }
                }
            }
        }

        fun copyFilesToDirectory() {
            val directory = File(targetDirectory).apply { if (!exists()) mkdirs() }
            val sql = "SELECT name, x, y, z, file_content FROM $tableName"

            Bukkit.getServer().logger.info("Checking the database for new files...")

            try {
                dataSource.connection.use { connection ->
                    connection.prepareStatement(sql).use { statement ->
                        statement.executeQuery().use { resultSet ->
                            while (resultSet.next()) {
                                val fileName = resultSet.getString("name")
                                if (processedFiles.contains(fileName)) continue

                                val content = """
                                world: 1c617a1b-94f2-4311-9ae2-c4102bf1e96f
                                world-name: world
                                x: ${resultSet.getFloat("x").toInt()}
                                y: ${resultSet.getFloat("y").toInt()}
                                z: ${resultSet.getFloat("z").toInt()}
                                yaw: 0
                                pitch: 0
                                name: $fileName
                                lastowner: ea0064bf-04dc-419e-8a0d-311c9a2b7a87
                            """.trimIndent()
                                File(directory, "$fileName.yml").writeText(content)
                                sendDiscordEmbed(fileName, resultSet.getFloat("x").toInt(), resultSet.getFloat("y").toInt(), resultSet.getFloat("z").toInt())
                                processedFiles.add(fileName)
                            }
                        }
                    }
                }
            } catch (e: SQLException) {
                Bukkit.getServer().logger.severe("Failed to retrieve files from database: ${e.message}")
            }
        }

        private fun sendDiscordEmbed(fileName: String, x: Int, y: Int, z: Int) {
            if (discordChannelId.isEmpty()) {
                Bukkit.getServer().logger.warning("Discord channel ID is not configured.")
                return
            }

            val mainGuild = DiscordSRV.getPlugin().mainGuild
            if (mainGuild == null) {
                Bukkit.getServer().logger.warning("Main guild is not available.")
                return
            }

            val embed = EmbedBuilder()
                .setTitle("File Created")
                .setDescription("A new file named **$fileName.yml** has been created.")
                .addField("Coordinates", "X: $x, Y: $y, Z: $z", false)
                .setColor(Color.GREEN)
                .build()

            mainGuild.jda.getTextChannelById(discordChannelId)?.sendMessageEmbeds(embed)?.queue()
                ?: Bukkit.getServer().logger.warning("Discord channel with ID $discordChannelId not found.")
        }

        fun close() {
            dataSource.close()
        }
    }
}