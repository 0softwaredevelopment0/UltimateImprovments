"""
Telegram бот для 2FA Minecraft сервера @OakworldSRVbot.
При /start отвечает Chat ID пользователя.
Запуск: python bot.py
"""

import asyncio
from aiogram import Bot, Dispatcher, types
from aiogram.filters import Command

API_TOKEN = "8770086137:AAHHv6ASBEeqHE2IxOmiq-HdC6xTD9R7D6g"

bot = Bot(token=API_TOKEN)
dp = Dispatcher()


@dp.message(Command("start"))
async def cmd_start(message: types.Message):
    chat_id = message.chat.id
    await message.answer(
        f"🤖 Бот 2FA для Minecraft сервера\n\n"
        f"📌 Ваш Chat ID: <b>{chat_id}</b>\n\n"
        f"Введите этот ID на сервере:\n"
        f"<code>/mp auth 2fa setup {chat_id}</code>\n\n"
        f"После настройки, при входе на сервер\n"
        f"вам будет приходить код подтверждения.",
        parse_mode="HTML"
    )


async def main():
    print("Bot started! https://t.me/OakworldSRVbot")
    await dp.start_polling(bot)


if __name__ == "__main__":
    asyncio.run(main())
