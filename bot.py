"""
Telegram Bot для 2FA — MC-Plugin
Получает POST от плагина, отправляет код в Telegram игроку

Установка:
  pip install aiogram aiohttp

Запуск:
  python bot.py
"""

from aiogram import Bot, Dispatcher
from aiogram.filters import Command
from aiohttp import web
import json
import logging

logging.basicConfig(level=logging.INFO)

# =========================================
# ⚡ ВСТАВЬ СВОЙ ТОКЕН СЮДА
# =========================================
API_TOKEN = "8770086137:AAHHv6ASBEeqHE2IxOmiq-HdC6xTD9R7D6g"

bot = Bot(token=API_TOKEN)
dp = Dispatcher()


@dp.message(Command("start"))
async def cmd_start(message):
    """Показывает Chat ID игрока при /start"""
    await message.answer(
        "🤖 Бот 2FA для Minecraft сервера\n\n"
        f"📌 Ваш Chat ID: {message.chat.id}\n\n"
        "Введите этот ID на сервере:\n"
        "/mp auth 2fa setup <ваш_chat_id>\n\n"
        "После настройки, при входе на сервер\n"
        "вам будет приходить код подтверждения."
    )
    logging.info(f"User {message.from_user.id} started bot, chat_id={message.chat.id}")


async def handle_send_code(request):
    """Принимает POST от плагина, отправляет код в Telegram"""
    try:
        data = await request.json()
        chat_id = data["chat_id"]
        code = data["code"]
        player = data["player"]

        logging.info(f"Sending code {code} to chat {chat_id} for player {player}")

        await bot.send_message(
            chat_id,
            f"🔐 <b>Код подтверждения</b>\n\n"
            f"Код: <b>{code}</b>\n"
            f"Игрок: {player}\n\n"
            f"❌ Никому не сообщайте этот код!",
        )

        return web.Response(status=200, text="OK")

    except Exception as e:
        logging.error(f"Error: {e}")
        return web.Response(status=500, text=str(e))


# Создаём aiohttp приложение
app = web.Application()
app.router.add_post("/send-code", handle_send_code)

if __name__ == "__main__":
    logging.info("Bot started on http://0.0.0.0:3000")
    web.run_app(app, host="0.0.0.0", port=3000)
