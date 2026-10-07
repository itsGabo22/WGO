"use client";

import React, { useState } from "react";
import { Volume2, VolumeX } from "lucide-react";
import { motion } from "framer-motion";
import { cn } from "@/lib/utils";

interface VoiceToggleProps {
  className?: string;
}

export function VoiceToggle({ className }: VoiceToggleProps) {
  const [isMuted, setIsMuted] = useState(false);

  return (
    <motion.button
      onClick={() => setIsMuted(!isMuted)}
      className={cn(
        "flex h-[48px] w-[48px] items-center justify-center rounded-full bg-surface border-[1.5px] border-black text-black",
        className
      )}
      whileTap={{ scale: 0.9 }}
      aria-label={isMuted ? "Unmute Voice Guidance" : "Mute Voice Guidance"}
    >
      {isMuted ? <VolumeX size={20} strokeWidth={2.5} /> : <Volume2 size={20} strokeWidth={2.5} />}
    </motion.button>
  );
}
