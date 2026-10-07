"use client";

import React from "react";
import { Mic } from "lucide-react";
import { motion } from "framer-motion";
import { cn } from "@/lib/utils";

interface PrimaryMicButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  isListening?: boolean;
}

export function PrimaryMicButton({ isListening = false, className, ...props }: PrimaryMicButtonProps) {
  return (
    <div className={cn("relative flex items-center justify-center", className)}>
      {isListening && (
        <>
          {/* Inner Ring */}
          <motion.div
            className="absolute rounded-full bg-primary/60"
            style={{ width: 116, height: 116 }}
            animate={{
              scale: [1, 1.12, 1],
            }}
            transition={{
              duration: 1.5,
              repeat: Infinity,
              ease: "easeInOut",
            }}
          />
          {/* Outer Ring */}
          <motion.div
            className="absolute rounded-full border-[1.5px] border-secondary/30"
            style={{ width: 134, height: 134 }}
            animate={{
              scale: [1, 1.15, 1],
              opacity: [0.3, 0.1, 0.3],
            }}
            transition={{
              duration: 1.5,
              repeat: Infinity,
              ease: "easeInOut",
              delay: 0.12,
            }}
          />
        </>
      )}
      
      {/* Mic Button */}
      <motion.button
        className={cn(
          "relative z-10 flex h-[104px] w-[104px] items-center justify-center rounded-full bg-primary text-white shadow-md outline-none transition-colors",
          isListening ? "bg-[#0D59F2]" : "border border-[#0038B8]"
        )}
        animate={isListening ? { scale: 1.04 } : { scale: 1 }}
        whileTap={{ scale: 0.95, backgroundColor: "#000000" }}
        {...props}
      >
        <Mic size={40} className={isListening ? "animate-pulse" : ""} />
      </motion.button>
    </div>
  );
}
